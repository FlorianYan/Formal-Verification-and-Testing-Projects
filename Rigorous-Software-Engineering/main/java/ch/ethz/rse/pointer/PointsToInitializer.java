package ch.ethz.rse.pointer;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import apron.Texpr1Node;
import ch.ethz.rse.utils.Constants;
import soot.Local;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JSpecialInvokeExpr;
import soot.jimple.internal.JVirtualInvokeExpr;
import soot.jimple.spark.pag.Node;

/**
 * Convenience class which helps determine the {@link FrogInitializer}s
 * potentially used to create objects pointed to by a given variable
 */
public class PointsToInitializer {

	private static final Logger logger = LoggerFactory.getLogger(PointsToInitializer.class);

	/**
	 * Internally used points-to analysis
	 */
	private final PointsToAnalysisWrapper pointsTo;

	/**
	 * class for which we are running points-to
	 */
	private final SootClass c;

	/**
	 * Maps abstract object indices to initializers
	 */
	private final Map<Node, FrogInitializer> initializers = new HashMap<Node, FrogInitializer>();

	/**
	 * All {@link FrogInitializer}s, keyed by method
	 */
	private final Multimap<SootMethod, FrogInitializer> perMethod = HashMultimap.create();

	public PointsToInitializer(SootClass c) {
		this.c = c;
		logger.debug("Running points-to analysis on " + c.getName());
		this.pointsTo = new PointsToAnalysisWrapper(c);
		logger.debug("Analyzing initializers in " + c.getName());
		this.analyzeAllInitializers();
	}

	private void analyzeAllInitializers() {
		int id = 0;  // unique id corrspending to labels

		for (SootMethod method : this.c.getMethods()) {

			if (method.getName().contains("<init>")) {
				// skip constructor of the class
				continue;
			}

			for (Unit u : method.getActiveBody().getUnits()) {
				if(!(u instanceof JInvokeStmt)){
					continue;
				}
				JInvokeStmt invokeStmt = (JInvokeStmt) u;
				
				if(!(invokeStmt.getInvokeExpr() instanceof JSpecialInvokeExpr)){
					continue;
				}

				JSpecialInvokeExpr invokeExpr = (JSpecialInvokeExpr )invokeStmt.getInvokeExpr();

				//call already given helper method
				if(isRelevantInit(invokeExpr)){
					Value v = invokeExpr.getArg(0);
					int productionCost = ((IntConstant) v).value;

					//local variable corresponding to the Frog 
					Local base = (Local) invokeExpr.getBase();

					//get all nodes that this local variable may point to
					Collection<Node> nodes = getAllocationNodes(invokeExpr);

					//create FrogInitializer and populate datastructures
					FrogInitializer frogInitializer = new FrogInitializer(invokeStmt, id++, productionCost);
					perMethod.put(method, frogInitializer);
					for(Node node:nodes){
						initializers.put(node, frogInitializer);
					}
				}
			}
		}
	}

	public Collection<FrogInitializer> getInitializers(SootMethod method) {
		return this.perMethod.get(method);
	}

	public List<FrogInitializer> pointsTo(Local base) {
		Collection<Node> nodes = this.pointsTo.getNodes(base);
		List<FrogInitializer> initializers = new LinkedList<FrogInitializer>();
		for (Node node : nodes) {
			FrogInitializer initializer = this.initializers.get(node);
			if (initializer != null) {
				// ignore nodes that were not initialized
				initializers.add(initializer);
			}
		}
		return initializers;
	}

	/**
	 * Returns all allocation nodes that could correspond to the given invokeExpression, which must be a call to Frog init function
	 * Note that more than one node can be returned.
	 */
	public Collection<Node> getAllocationNodes(JSpecialInvokeExpr invokeExpr){
		if(!isRelevantInit(invokeExpr)){
			throw new RuntimeException("Call to getAllocationNodes with " + invokeExpr.toString() + "which is not an init call for the Frog class");
		}
		Local base = (Local) invokeExpr.getBase();
		Collection<Node> allocationNodes = this.pointsTo.getNodes(base);
		return allocationNodes;
	}

	public boolean isRelevantInit(JSpecialInvokeExpr invokeExpr){
		Local base = (Local) invokeExpr.getBase();
		boolean isRelevant = base.getType().toString().equals(Constants.FrogClassName);
		boolean isInit = invokeExpr.getMethod().getName().equals("<init>");
		return isRelevant && isInit;
	}
}
