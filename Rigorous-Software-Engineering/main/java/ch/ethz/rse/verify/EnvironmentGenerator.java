package ch.ethz.rse.verify;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.Iterables;

import apron.Environment;
import ch.ethz.rse.pointer.FrogInitializer;
import ch.ethz.rse.pointer.PointsToInitializer;
import ch.ethz.rse.utils.Constants;
import soot.IntegerType;
import soot.Local;
import soot.PointsToAnalysis;
import soot.SootMethod;
import soot.Value;
import soot.jimple.ParameterRef;
import soot.jimple.internal.JimpleLocal;
import soot.util.Chain;
import soot.Unit;
import soot.jimple.DefinitionStmt;


import org.slf4j.Logger;  
import org.slf4j.LoggerFactory; 

/**
 * Generates an environment which holds all variable names needed for the
 * numerical analysis of a method
 *
 */
public class EnvironmentGenerator {

	private final SootMethod method;

	private final PointsToInitializer pointsTo;

	private static final Logger logger = LoggerFactory.getLogger(EnvironmentGenerator.class);

	/**
	 * List of names for integer variables relevant when analyzing the program
	 */
	private List<String> ints = new LinkedList<String>();

	private final Environment env;

	/**
	 * 
	 * @param method
	 */
	public EnvironmentGenerator(SootMethod method, PointsToInitializer pointsTo) {
		this.method = method;
		this.pointsTo = pointsTo;

		// populate this.ints
		populateInts();

        String ints_arr[] = Iterables.toArray(this.ints, String.class);

		
		String reals[] = {}; // we are not analyzing real numbers
		this.env = new Environment(ints_arr, reals);
	}

	public Environment getEnvironment() {
		return this.env;
	}

	private void populateInts() {
        for (Unit u : method.getActiveBody().getUnits()) {
            if (u instanceof DefinitionStmt) {
                DefinitionStmt sd = (DefinitionStmt) u;
                Value left = sd.getLeftOp();

                if (left instanceof JimpleLocal) {
                    JimpleLocal local = (JimpleLocal) left;
                    if (local.getType() instanceof IntegerType && !ints.contains(local.getName())) {
                        ints.add(local.getName());
                        logger.debug("Added integer variable: " + local.getName());
                    }
                }
            }
        }
    }

}
