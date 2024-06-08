package ch.ethz.rse.numerical;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import apron.Abstract1;
import apron.ApronException;
import apron.Environment;
import apron.Manager;
import apron.MpqScalar;
import apron.Polka;
import apron.Tcons1;
import apron.Texpr1BinNode;
import apron.Texpr1CstNode;
import apron.Texpr1Intern;
import apron.Texpr1Node;
import apron.Texpr1UnNode;
import apron.Texpr1VarNode;
import ch.ethz.rse.VerificationProperty;
import ch.ethz.rse.pointer.FrogInitializer;
import ch.ethz.rse.pointer.PointsToInitializer;
import ch.ethz.rse.utils.Constants;
import ch.ethz.rse.verify.EnvironmentGenerator;
import soot.ArrayType;
import soot.DoubleType;
import soot.Local;
import soot.RefType;
import soot.SootHelper;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.Body;
import soot.jimple.AddExpr;
import soot.jimple.BinopExpr;
import soot.jimple.ConditionExpr;
import soot.jimple.DefinitionStmt;
import soot.jimple.IfStmt;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.MulExpr;
import soot.jimple.NopStmt;
import soot.jimple.ParameterRef;
import soot.jimple.Stmt;
import soot.jimple.SubExpr;
import soot.jimple.internal.AbstractBinopExpr;
import soot.jimple.internal.JAddExpr;
import soot.jimple.internal.JArrayRef;
import soot.jimple.internal.JEqExpr;
import soot.jimple.internal.JGeExpr;
import soot.jimple.internal.JGotoStmt;
import soot.jimple.internal.JGtExpr;
import soot.jimple.internal.JIfStmt;
import soot.jimple.internal.JInstanceFieldRef;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JLeExpr;
import soot.jimple.internal.JLtExpr;
import soot.jimple.internal.JMulExpr;
import soot.jimple.internal.JNeExpr;
import soot.jimple.internal.JReturnVoidStmt;
import soot.jimple.internal.JSpecialInvokeExpr;
import soot.jimple.internal.JSubExpr;
import soot.jimple.internal.JVirtualInvokeExpr;
import soot.jimple.internal.JimpleLocal;
import soot.jimple.toolkits.annotation.logic.Loop;
import soot.toolkits.graph.LoopNestTree;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.ForwardBranchedFlowAnalysis;
import soot.util.Chain;

/**
 * Convenience class running a numerical analysis on a given {@link SootMethod}
 */
public class NumericalAnalysis extends ForwardBranchedFlowAnalysis<NumericalStateWrapper> {

	private static final Logger logger = LoggerFactory.getLogger(NumericalAnalysis.class);

	private final SootMethod method;

	/**
	 * the property we are verifying
	 */
	private final VerificationProperty property;

	/**
	 * the pointer analysis result we are verifying
	 */
	private final PointsToInitializer pointsTo;

	/**
	 * number of times this loop head was encountered during analysis
	 */
	private HashMap<Unit, IntegerWrapper> loopHeads = new HashMap<Unit, IntegerWrapper>();
	/**
	 * Previously seen abstract state for each loop head
	 */
	private HashMap<Unit, NumericalStateWrapper> loopHeadState = new HashMap<Unit, NumericalStateWrapper>();

	/**
	 * Numerical abstract domain to use for analysis: Convex polyhedra
	 */
	public final Manager man = new Polka(true);

	public Environment env;

	/**
	 * We apply widening after updating the state at a given merge point for the
	 * {@link WIDENING_THRESHOLD}th time
	 */
	private static final int WIDENING_THRESHOLD = 6;

	/**
	 * 
	 * @param method   method to analyze
	 * @param property the property we are verifying
	 */
	public NumericalAnalysis(SootMethod method, VerificationProperty property, PointsToInitializer pointsTo) {
		super(SootHelper.getUnitGraph(method));

		this.property = property;

		this.pointsTo = pointsTo;
		
		this.method = method;

		this.env = new EnvironmentGenerator(method, pointsTo).getEnvironment();

		// This if statement is not part of the skeleton
		if(this.property == VerificationProperty.OVERALL_PROFIT){
			String[] integer_new = { "overall_profit", "overall_profit_2" };
        	String[] real_new = {};
			this.env = this.env.add(integer_new, real_new);

			// also add nop statement to the end (Then use this extra unit to read out the final state of overall_profit in Verifier)
			Body body = method.retrieveActiveBody();
			NopStmt nopStmt = Jimple.v().newNopStmt();
			Chain<Unit> units = body.getUnits();
			//units.add(nopStmt);
		}

		UnitGraph g = SootHelper.getUnitGraph(method);

		// initialize counts for loop heads
		for (Loop l : new LoopNestTree(g.getBody())) {
			loopHeads.put(l.getHead(), new IntegerWrapper(0));
		}

		// perform analysis by calling into super-class
		logger.info("Analyzing {} in {}", method.getName(), method.getDeclaringClass().getName());
		doAnalysis(); // calls newInitialFlow, entryInitialFlow, merge, flowThrough, and stops when a fixed point is reached
	}

	/**
	 * Report unhandled instructions, types, cases, etc.
	 * 
	 * @param task description of current task
	 * @param what
	 */
	public static void unhandled(String task, Object what, boolean raiseException) {
		String description = task + ": Can't handle " + what.toString() + " of type " + what.getClass().getName();

		if (raiseException) {
			logger.error("Raising exception " + description);
			throw new UnsupportedOperationException(description);
		} else {
			logger.error(description);

			// print stack trace
			StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
			for (int i = 1; i < stackTrace.length; i++) {
				logger.error(stackTrace[i].toString());
			}
		}
	}

	@Override
	protected void copy(NumericalStateWrapper source, NumericalStateWrapper dest) {
		source.copyInto(dest);
	}

	@Override
	protected NumericalStateWrapper newInitialFlow() {
		// should be bottom (only entry flows are not bottom originally)
		return NumericalStateWrapper.bottom(man, env);
	}

	@Override
	protected NumericalStateWrapper entryInitialFlow() {
		// state of entry points into function
		NumericalStateWrapper ret = NumericalStateWrapper.top(man, env);

		if(this.property == VerificationProperty.OVERALL_PROFIT){
			Texpr1Node zeroNode = new Texpr1CstNode(new MpqScalar(0));
			Texpr1Intern zeroExpr = new Texpr1Intern(env, zeroNode);
			Abstract1 absTop = ret.get();
			try {
				absTop = absTop.assignCopy(man, "overall_profit", zeroExpr, null);
				absTop = absTop.assignCopy(man, "overall_profit_2", zeroExpr, null);
			} catch (ApronException e) {
				logger.error("entryInititalFlow: ApronException!");
				return ret;
			}
			ret.set(absTop);
		}

		return ret;
	}

	@Override
	protected void merge(Unit succNode, NumericalStateWrapper w1, NumericalStateWrapper w2, NumericalStateWrapper w3) {
		logger.debug("Merging for Node: " + succNode);
		try {
			Abstract1 state1 = w1.get();
			Abstract1 state2 = w2.get();
			Abstract1 merged_state = state1.joinCopy(man, state2);
			NumericalStateWrapper temp = new NumericalStateWrapper(man, merged_state);
			if (!loopHeadState.containsKey(succNode)) {
				loopHeadState.put(succNode, temp);
			}

			Abstract1 old_state = loopHeadState.get(succNode).get();
			w3.set(merged_state);  

			if (loopHeads.containsKey(succNode)) {
				int count = loopHeads.get(succNode).value;
				count++;
				loopHeads.get(succNode).value = count;
				
				if (count >= WIDENING_THRESHOLD) {
		
					Abstract1 joined_state = merged_state.joinCopy(man, old_state);
					Abstract1 widened_state = old_state.widening(man, joined_state);
					NumericalStateWrapper temp2 =  new NumericalStateWrapper(man, widened_state);
					loopHeadState.put(succNode, temp2);
					w3.set(widened_state);
				}
			}
			else {
				return;
			}
		} catch (ApronException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void merge(NumericalStateWrapper src1, NumericalStateWrapper src2, NumericalStateWrapper trg) {
		// this method is never called, we are using the other merge instead
		throw new UnsupportedOperationException();
	}

	@Override
	protected void flowThrough(NumericalStateWrapper inWrapper, Unit op, List<NumericalStateWrapper> fallOutWrappers,
			List<NumericalStateWrapper> branchOutWrappers) {
		logger.debug(inWrapper + " " + op + " => ?");

		Stmt s = (Stmt) op;

		// fallOutWrapper is the wrapper for the state after running op,
		// assuming we move to the next statement. Do not overwrite
		// fallOutWrapper, but use its .set method instead
		assert fallOutWrappers.size() <= 1;
		NumericalStateWrapper fallOutWrapper = null;
		if (fallOutWrappers.size() == 1) {
			fallOutWrapper = fallOutWrappers.get(0);
			inWrapper.copyInto(fallOutWrapper);
		}

		// branchOutWrapper is the wrapper for the state after running op,
		// assuming we follow a conditional jump. It is therefore only relevant
		// if op is a conditional jump. In this case, (i) fallOutWrapper
		// contains the state after "falling out" of the statement, i.e., if the
		// condition is false, and (ii) branchOutWrapper contains the state
		// after "branching out" of the statement, i.e., if the condition is
		// true.
		assert branchOutWrappers.size() <= 1;
		NumericalStateWrapper branchOutWrapper = null;
		if (branchOutWrappers.size() == 1) {
			branchOutWrapper = branchOutWrappers.get(0);
			inWrapper.copyInto(branchOutWrapper);
		}

		try {
			if (s instanceof DefinitionStmt) {
				// handle assignment

				DefinitionStmt sd = (DefinitionStmt) s;
				Value left = sd.getLeftOp();
				Value right = sd.getRightOp();

				// We are not handling these cases:
				if (!(left instanceof JimpleLocal)) {
					unhandled("Assignment to non-local variable", left, true);
				} else if (left instanceof JArrayRef) {
					unhandled("Assignment to a non-local array variable", left, true);
				} else if (left.getType() instanceof ArrayType) {
					unhandled("Assignment to Array", left, true);
				} else if (left.getType() instanceof DoubleType) {
					unhandled("Assignment to double", left, true);
				} else if (left instanceof JInstanceFieldRef) {
					unhandled("Assignment to field", left, true);
				}

				if (left.getType() instanceof RefType) {
					// assignments to references are handled by pointer analysis
					// no action necessary
				} else {
					// handle assignment
					handleDef(fallOutWrapper, left, right);
				}

			} else if (s instanceof JIfStmt) {
				// handle if
				JIfStmt ifStmt = (JIfStmt) s;
				Value condition = ifStmt.getCondition();
				ConditionExpr condition_expression = (ConditionExpr) condition;
				Value op1 = condition_expression.getOp1();
				Value op2 = condition_expression.getOp2();
				Texpr1Node op1_node = convertValueToTexpr1Node(op1);
				Texpr1Node op2_node = convertValueToTexpr1Node(op2);
				// Now we want to encode op1 - op2
				Texpr1Node subop1op2 = new Texpr1BinNode(Texpr1BinNode.OP_SUB, op1_node, op2_node);

				// Create the appropriate constraint
				Tcons1 trueConstraint = null;
				Tcons1 falseConstraint = null;
				if (condition_expression instanceof JEqExpr) {
					trueConstraint = new Tcons1(env, Tcons1.EQ, subop1op2);
					falseConstraint = new Tcons1(env, Tcons1.DISEQ, subop1op2);
				} else if (condition_expression instanceof JGeExpr) {
					trueConstraint = new Tcons1(env, Tcons1.SUPEQ, subop1op2);
					falseConstraint = new Tcons1(env, Tcons1.SUP, new Texpr1UnNode(Texpr1UnNode.OP_NEG, subop1op2));
				} else if (condition_expression instanceof JGtExpr) {
					trueConstraint = new Tcons1(env, Tcons1.SUP, subop1op2);
					falseConstraint = new Tcons1(env, Tcons1.SUPEQ, new Texpr1UnNode(Texpr1UnNode.OP_NEG, subop1op2));
				} else if (condition_expression instanceof JLeExpr) {
					trueConstraint = new Tcons1(env, Tcons1.SUPEQ, new Texpr1UnNode(Texpr1UnNode.OP_NEG, subop1op2));
					falseConstraint = new Tcons1(env, Tcons1.SUP, subop1op2);
				} else if (condition_expression instanceof JLtExpr) {
					trueConstraint = new Tcons1(env, Tcons1.SUP, new Texpr1UnNode(Texpr1UnNode.OP_NEG, subop1op2));
					falseConstraint = new Tcons1(env, Tcons1.SUPEQ, subop1op2);
				} else if (condition_expression instanceof JNeExpr) {
					trueConstraint = new Tcons1(env, Tcons1.DISEQ, subop1op2);
					falseConstraint = new Tcons1(env, Tcons1.EQ, subop1op2);
				} else {
					logger.debug("Illegal if statement");
				}

				// Apply the constraints to the respective states
				if (trueConstraint != null && branchOutWrapper != null) {
					branchOutWrapper.get().meet(man, trueConstraint);
				}
				if (falseConstraint != null && fallOutWrapper != null) {
					fallOutWrapper.get().meet(man, falseConstraint);
				}


			} else if (s instanceof JInvokeStmt) {
				// handle invocations
				JInvokeStmt jInvStmt = (JInvokeStmt) s;
				InvokeExpr invokeExpr = jInvStmt.getInvokeExpr();
				if (invokeExpr instanceof JVirtualInvokeExpr) {
					handleInvoke(jInvStmt, fallOutWrapper);
				} else if (invokeExpr instanceof JSpecialInvokeExpr) {
					// ignoring this
				} else {
					unhandled("Unhandled invoke statement", invokeExpr, true);
				}
			} else if (s instanceof JGotoStmt) {
				// safe to ignore
			} else if (s instanceof JReturnVoidStmt) {
				// safe to ignore
			} else if (s instanceof NopStmt){
				// safe to ignore
			} else {
				unhandled("Unhandled statement", s, true);
			}

			// log outcome
			if (fallOutWrapper != null) {
				logger.debug(inWrapper.get() + " " + s + " =>[fallout] " + fallOutWrapper);
			}
			if (branchOutWrapper != null) {
				logger.debug(inWrapper.get() + " " + s + " =>[branchout] " + branchOutWrapper);
			}

		} catch (ApronException e) {
			throw new RuntimeException(e);
		}
	}

	public static Texpr1Node convertValueToTexpr1Node(Value value) {
        if (value instanceof IntConstant) {
            // Convert IntConstant to Texpr1CstNode
            IntConstant intConstant = (IntConstant) value;
            return new Texpr1CstNode(new MpqScalar(intConstant.value));
        } else if (value instanceof JimpleLocal) {
            // Convert JimpleLocal to Texpr1VarNode
            JimpleLocal local = (JimpleLocal) value;
            return new Texpr1VarNode(local.getName());
        } else {
            // Handle other cases or throw an exception
            throw new UnsupportedOperationException("Unsupported Value type: " + value.getClass());
        }
    }

	public void handleInvoke(JInvokeStmt jInvStmt, NumericalStateWrapper fallOutWrapper) throws ApronException {
		if (this.property == VerificationProperty.OVERALL_PROFIT) {
			if (fallOutWrapper.get().isBottom(man)) {
				// means that this state/ expression can't really be reached
				return;
			}

			// Get the invoke expression
			InvokeExpr invokeExpr = jInvStmt.getInvokeExpr();
			JVirtualInvokeExpr sellExpr = (JVirtualInvokeExpr)invokeExpr;
			// Get the base of the invoke expression
			Local baseNode = (Local) sellExpr.getBase();
			// Get the method name
			//Get all the initilzisers the baseNode might point to
			List<FrogInitializer> frogInitializers = pointsTo.pointsTo(baseNode);

			// Find the worst case production cost frog, i.e. the frog with the highest production cost
			FrogInitializer maxFrog = null;
			for (FrogInitializer frogInitializer : frogInitializers) {
				if (maxFrog == null || frogInitializer.argument > maxFrog.argument) {
					maxFrog = frogInitializer;
				}
			}
			Abstract1 currentState = fallOutWrapper.get();
			Value priceValue = sellExpr.getArg(0);
			IntConstant max_frog_production_cost = IntConstant.v(maxFrog.argument);
			String leftName = "overall_profit";
			Texpr1Node subExpressionNode = valueToTexpr1Node(new JSubExpr(priceValue, max_frog_production_cost));
			Texpr1Node overallProfitNode = new Texpr1VarNode("overall_profit");
			Texpr1Intern overallProfitIntern = new Texpr1Intern(env, overallProfitNode);
			currentState.assign(man, "overall_profit_2", overallProfitIntern, null);

			Texpr1Node overallProfitNode2 = new Texpr1VarNode("overall_profit_2");
			Texpr1Node addNode = new Texpr1BinNode(Texpr1BinNode.OP_ADD, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, subExpressionNode, overallProfitNode2);
			Texpr1Intern rightExpression = new Texpr1Intern(env, addNode);
			currentState.assign(man, leftName, rightExpression, null);
			logger.debug("handleInvoke: Interval of overall_profit: {}", currentState.getBound(man, "overall_profit").toString());

			fallOutWrapper.set(currentState);
		}
	}

	// returns state of in after assignment
	private void handleDef(NumericalStateWrapper outWrapper, Value left, Value right) throws ApronException {
		if (right instanceof ParameterRef) {
			return;
		}
		// Retrieve the current abstract state.
		Abstract1 currentState = outWrapper.get();

		// Get the name of the left value, assuming it is a JimpleLocal.
		String leftName = ((JimpleLocal) left).getName();
	
		// Compile the right value into a Texpr1Intern expression.
		Texpr1Intern rightExpression = new Texpr1Intern(env, valueToTexpr1Node(right));
		// Assign the compiled expression to the left variable in the abstract state.
		currentState.assign(man, leftName, rightExpression, null);

		// Update the wrapper with the new state.
		outWrapper.set(currentState);

	}
	
	// covenience/helper methods
	private Texpr1Node valueToTexpr1Node(Value expr) {
		switch (expr.getClass().getSimpleName()) {
			case "IntConstant":
				IntConstant intConstant = (IntConstant) expr;
				int intValue = intConstant.value;
				return new Texpr1CstNode(new MpqScalar(intValue));
	
			case "JimpleLocal":
				JimpleLocal jimpleLocal = (JimpleLocal) expr;
				String name = jimpleLocal.getName();
				return new Texpr1VarNode(name);

			case "JAddExpr":
				JAddExpr addExpr = (JAddExpr) expr;
				Texpr1Node add_op1 = valueToTexpr1Node(addExpr.getOp1());
				Texpr1Node add_op2 = valueToTexpr1Node(addExpr.getOp2());
				return new Texpr1BinNode(Texpr1BinNode.OP_ADD, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, add_op1, add_op2);
		
			case "JSubExpr":
				JSubExpr subExpr = (JSubExpr) expr;
				Texpr1Node sub_op1 = valueToTexpr1Node(subExpr.getOp1());
				Texpr1Node sub_op2 = valueToTexpr1Node(subExpr.getOp2());
				return new Texpr1BinNode(Texpr1BinNode.OP_SUB, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, sub_op1, sub_op2);
	
			case "JMulExpr":
				JMulExpr mulExpr = (JMulExpr) expr;
				Texpr1Node mul_op1 = valueToTexpr1Node(mulExpr.getOp1());
				Texpr1Node mul_op2 = valueToTexpr1Node(mulExpr.getOp2());
				return new Texpr1BinNode(Texpr1BinNode.OP_MUL, Texpr1BinNode.RTYPE_INT, Texpr1BinNode.RDIR_ZERO, mul_op1, mul_op2);
	
			default:
				throw new RuntimeException("Unable to convert expression: " + expr.toString());
		}
	}
}
