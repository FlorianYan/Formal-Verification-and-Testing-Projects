package ch.ethz.rse.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import apron.Environment;
import apron.Var;

import org.junit.jupiter.api.Assertions;

import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocal;
import ch.ethz.rse.pointer.PointsToInitializer;
import ch.ethz.rse.verify.EnvironmentGenerator;

import java.util.Collections;

public class EnvironmentGeneratorTest {

    private SootMethod mockMethod;
    private PointsToInitializer mockPointsTo;
    private EnvironmentGenerator envGen;

    @BeforeEach
    public void setUp() {
        SootClass mockClass = new SootClass("MockClass");
        mockMethod = new SootMethod("mockMethod", Collections.emptyList(), VoidType.v());
        mockClass.addMethod(mockMethod);

        Body body = Jimple.v().newBody(mockMethod);
        mockMethod.setActiveBody(body);

        // Adding some integer locals to the method body
        Local intLocal1 = new JimpleLocal("intVar1", IntType.v());
        Local intLocal2 = new JimpleLocal("intVar2", IntType.v());

        body.getLocals().add(intLocal1);
        body.getLocals().add(intLocal2);

        // Add statements to the method body to simulate actual code
        Unit stmt1 = Jimple.v().newAssignStmt(intLocal1, IntConstant.v(0));
        Unit stmt2 = Jimple.v().newAssignStmt(intLocal2, IntConstant.v(1));
        body.getUnits().add(stmt1);
        body.getUnits().add(stmt2);

        mockPointsTo = new PointsToInitializer(mockClass);
        envGen = new EnvironmentGenerator(mockMethod, mockPointsTo);
    }

    @Test
    public void testEnvironmentInitialization() {
        Assertions.assertNotNull(envGen.getEnvironment(), "Environment should not be null");
    }

    @Test
    public void testIntsPopulation() {
        Environment env = envGen.getEnvironment();
        Var[] intVars = env.getIntVars();

        Assertions.assertNotNull(intVars, "Integer variables array should not be null");
        Assertions.assertTrue(intVars.length > 0, "Integer variables array should not be empty");

        // Check if the environment contains the expected variable names
        boolean containsIntVar1 = false;
        boolean containsIntVar2 = false;
        for (Var var : intVars) {
            if (var.toString().equals("intVar1")) {
                containsIntVar1 = true;
                System.out.println("first var found");
            }
            if (var.toString().equals("intVar2")) {
                containsIntVar2 = true;
                System.out.println("second var found");
            }
        }
        Assertions.assertTrue(containsIntVar1, "Environment should contain 'intVar1'");
        Assertions.assertTrue(containsIntVar2, "Environment should contain 'intVar2'");
    }

    @Test
    public void testNoReals() {
        Environment env = envGen.getEnvironment();
        Var[] realVars = env.getRealVars();

        Assertions.assertNotNull(realVars, "Real variables array should not be null");
        Assertions.assertEquals(0, realVars.length, "There should be no real variables");
    }
}
