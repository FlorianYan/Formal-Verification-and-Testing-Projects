package ch.ethz.rse.verify;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import apron.Abstract1;
import apron.ApronException;
import apron.Coeff;
import apron.Interval;
import apron.MpqScalar;
import apron.Tcons1;
import apron.Texpr1BinNode;
import apron.Texpr1CstNode;
import apron.Texpr1Node;
import apron.Texpr1VarNode;
import ch.ethz.rse.VerificationProperty;
import ch.ethz.rse.numerical.NumericalAnalysis;
import ch.ethz.rse.numerical.NumericalStateWrapper;
import ch.ethz.rse.pointer.FrogInitializer;
import ch.ethz.rse.pointer.PointsToInitializer;
import ch.ethz.rse.utils.Constants;
import polyglot.ast.Call;
import soot.Local;
import soot.SootClass;
import soot.SootHelper;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.NopStmt;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JSpecialInvokeExpr;
import soot.jimple.internal.JVirtualInvokeExpr;
import soot.jimple.internal.JimpleLocal;
import soot.toolkits.graph.UnitGraph;
import soot.util.Chain;
import soot.jimple.ParameterRef;
import java.util.List;

/**
 * Main class handling verification
 * 
 */
public class Verifier extends AVerifier {

	private static final Logger logger = LoggerFactory.getLogger(Verifier.class);

	/**
	 * class to be verified
	 */
	private final SootClass c;

	/**
	 * points to analysis for verified class
	 */
	private final PointsToInitializer pointsTo;

	/**
	 * 
	 * @param c class to verify
	 */
	public Verifier(SootClass c) {
		logger.debug("Analyzing {}", c.getName());

		this.c = c;

		// pointer analysis
		this.pointsTo = new PointsToInitializer(this.c);
	}

	protected void runNumericalAnalysis(VerificationProperty property) {
		// Loop through each method in the class 'c'
		for (SootMethod method : c.getMethods()) {
			logger.debug("Running analysis on Method {}", method.getName());
			// Skip methods that are abstract, native, or phantom
			if (!method.isConcrete()) {
				continue;
			}
			// Perform the numerical analysis on the current method
			NumericalAnalysis numericalAnalysisInstance = new NumericalAnalysis(method, property, this.pointsTo);
			this.numericalAnalysis.put(method, numericalAnalysisInstance);
		}
	}

	@Override
	public boolean checksNonNegative() {
		// iterate over all analyzed methods
		for (SootMethod m : this.numericalAnalysis.keySet()) {
			logger.debug("Checking NonNegative Property on Method {}", m.getName());
			NumericalAnalysis analysis = this.numericalAnalysis.get(m);
			apron.Environment env = analysis.env;
			apron.Manager man = analysis.man;

			//iterate over all units of the analyzed methods
			for(Unit u: m.getActiveBody().getUnits()){
				//only look at units that are calls to sell(v)
				if (!(u instanceof JInvokeStmt)) {
					continue;
				}
				JInvokeStmt invokeStmt = (JInvokeStmt)u;
				if(!(invokeStmt.getInvokeExpr() instanceof JVirtualInvokeExpr)){
					continue;
				}
				JVirtualInvokeExpr sellExpr = (JVirtualInvokeExpr)invokeStmt.getInvokeExpr();
				//get the state of the analysis result
				NumericalStateWrapper afterFlow = analysis.getFlowBefore(u);
				Abstract1 state = afterFlow.get();

				//Test NON_NEGATIVE of the state by finding the local variable v
				//that is passed into sell(v) and test v>=0
				//check the argument passed into sell(v), there is only 1, so we get the one at index 0
				Value v = sellExpr.getArg(0);
				//if we pass a constant
				if(v instanceof IntConstant){
					IntConstant vConstant = (IntConstant) v;
					logger.debug("The Value is an IntConstant with value {}", vConstant.value);
					if(vConstant.value < 0){
						return false;
					}
				}
				//if we pass a local variable
				else{
					String vName = "";
					if(v instanceof JimpleLocal){
						vName = ((JimpleLocal)v).getName();
						logger.debug("The Variable name vName is {} (JimpleLocal)", vName);
					}
					else if(v instanceof ParameterRef){
						int paramIndex = ((ParameterRef)v).getIndex();
						vName = m.getActiveBody().getParameterLocal(paramIndex).getName();
						logger.debug("The Variable name vName is {} (ParameterRef)", vName);
					}
					else{
						System.err.println("sell is called with neither local, constant or a parameter.");
						return false;
					}
					
					//encode constraint x>=0
					Texpr1Node vNode = new Texpr1VarNode(vName);
					Tcons1 constraint = new Tcons1(env, Tcons1.SUPEQ, vNode);
					
					//check if constraint is satisfied
					try {
						if(!state.satisfy(man, constraint)){
							return false;
						}
					} catch (ApronException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return true;
	}


	@Override
	public boolean checkItemProfit() {
		for(SootMethod m: this.numericalAnalysis.keySet()){
			logger.debug("Checking NonNegative Property on Method {}", m.getName());
			NumericalAnalysis analysis = this.numericalAnalysis.get(m);
			apron.Environment env = analysis.env;
			apron.Manager man = analysis.man;

			//iterate over all units of the analyzed methods
			for(Unit u: m.getActiveBody().getUnits()){
				//only look at units that are calls to sell(v)
				if (!(u instanceof JInvokeStmt)) {
					continue;
				}
				JInvokeStmt invokeStmt = (JInvokeStmt)u;
				if(!(invokeStmt.getInvokeExpr() instanceof JVirtualInvokeExpr)){
					continue;
				}
				JVirtualInvokeExpr sellExpr = (JVirtualInvokeExpr)invokeStmt.getInvokeExpr();
				//get the state of the analysis result
				NumericalStateWrapper afterFlow = analysis.getFlowBefore(u);
				Abstract1 state = afterFlow.get();
				Value v = sellExpr.getArg(0);

				Local baseNode = (Local) sellExpr.getBase();

				List<FrogInitializer> frogInitializers = pointsTo.pointsTo(baseNode);

				//should never be the case
				if(frogInitializers.isEmpty()){
					System.err.println("No frogs for this sell invocation.");
					return false;
				}

				for(int i = 0; i < frogInitializers.size(); i++){
					FrogInitializer frogInitializer = frogInitializers.get(i);

					int productionCost = frogInitializer.argument;

					//now check item profit
					if(v instanceof IntConstant){
						int vValue = ((IntConstant)v).value;
						if(vValue-productionCost < 0){
							return false;
						}
					}
					else{
						String vName = "";
						if(v instanceof JimpleLocal){
							vName = ((JimpleLocal) v).getName();
						}
						else if(v instanceof ParameterRef){
							int paramIndex = ((ParameterRef)v).getIndex();
							vName = m.getActiveBody().getParameterLocal(paramIndex).getName();
							logger.debug("The Variable name vName is {}", vName);
						}
						else{
							System.err.println("sell is called with neither local, constant or a parameter.");
							return false;
						}

						//encode constraint v-productionCost >= 0
						Coeff productionValueCoeff= new MpqScalar(-productionCost);
						Texpr1Node productionValueNode = new Texpr1CstNode(productionValueCoeff);
						Texpr1Node vNode = new Texpr1VarNode(vName);
						Texpr1Node itemProfitNode = new Texpr1BinNode(Texpr1BinNode.OP_ADD, vNode, productionValueNode);
						Tcons1 constraint = new Tcons1(env, Tcons1.SUPEQ, itemProfitNode);
						
						//check if constraint is satisfied
						try {
							if(!state.satisfy(man, constraint)){
								return false;
							}
						} catch (ApronException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
		return true;
	}


	@Override
	public boolean checkOverallProfit() {
		for (SootMethod m : this.numericalAnalysis.keySet()){
			logger.debug("Checking OverallProfit Property on Method {}", m.getName());
			NumericalAnalysis analysis = this.numericalAnalysis.get(m);
			apron.Environment env = analysis.env;
			apron.Manager man = analysis.man;
			//iterate over all units of the analyzed methods
			Chain<Unit> units = m.getActiveBody().getUnits();
			Unit lastUnit = units.getLast();

			NumericalStateWrapper afterFlow = analysis.getFlowBefore(lastUnit);
			Abstract1 state = afterFlow.get();
			Texpr1Node overallProfit = new Texpr1VarNode("overall_profit");
			// encode overall_profit >= 0
			Tcons1 constraint = new Tcons1(env, Tcons1.SUPEQ, overallProfit);
			try {
				Interval interval = state.getBound(man, "overall_profit");
				logger.debug("Interval of overall_profit: {}", interval.toString());
				if(!state.satisfy(man, constraint)){
					return false;
				}
			} catch (ApronException e) {
				logger.error("checkOverallProfit: ApronException!");
			}
		}
		return true;
	}
}
