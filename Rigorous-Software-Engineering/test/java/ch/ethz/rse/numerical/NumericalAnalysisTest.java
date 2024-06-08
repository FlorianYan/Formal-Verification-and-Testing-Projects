package ch.ethz.rse.numerical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import apron.*;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.toolkits.graph.UnitGraph;
import ch.ethz.rse.VerificationProperty;
import ch.ethz.rse.VerificationTask;
import ch.ethz.rse.pointer.PointsToInitializer;
import ch.ethz.rse.verify.ClassToVerify;
import ch.ethz.rse.verify.EnvironmentGenerator;

import java.util.Collections;

public class NumericalAnalysisTest {

    private SootMethod mockMethod;
    private VerificationProperty mockProperty;
    private PointsToInitializer mockPointsTo;
    private UnitGraph mockUnitGraph;
    private NumericalAnalysis analysis;
    private Manager man;
    private Environment env;

    @BeforeEach
    public void setUp() throws ApronException {
        String packageName = "ch.ethz.rse.integration.tests.Basic_Test_Safe";
        VerificationTask t = new VerificationTask(packageName, VerificationProperty.NON_NEGATIVE);

        ClassToVerify ctv = t.getTestClass();
        SootClass sc = SootHelper.loadClassAndAnalyze(ctv);

        mockMethod = sc.getMethodByName("m1");
        mockProperty = t.property;
        mockPointsTo = new PointsToInitializer(sc);
        mockUnitGraph = SootHelper.getUnitGraph(mockMethod);
        
        man = new Polka(true);
        analysis = new NumericalAnalysis(mockMethod, mockProperty, mockPointsTo);
        env = analysis.env;
    }

    @Test
    public void testInitialization() {
        Assertions.assertNotNull(analysis.man, "Manager should not be null");
        Assertions.assertTrue(analysis.man instanceof Polka, "Manager should be instance of Polka");
        Assertions.assertNotNull(analysis.env, "Environment should not be null");
    }

    @Test
    public void testNewInitialFlow() throws ApronException {
        NumericalStateWrapper initialFlow = analysis.newInitialFlow();
        Assertions.assertTrue(initialFlow.get().isBottom(man), "Initial flow should be at bottom");
    }

    @Test
    public void testEntryInitialFlow() throws ApronException {
        NumericalStateWrapper entryFlow = analysis.entryInitialFlow();
        Assertions.assertTrue(entryFlow.get().isTop(man), "Entry flow should be at top");
    }

    @Test
    public void testMerge() throws ApronException {
        Unit mockUnit = new JNopStmt();
        NumericalStateWrapper mockW1 = NumericalStateWrapper.bottom(man, env);
        NumericalStateWrapper mockW2 = NumericalStateWrapper.bottom(man, env);
        NumericalStateWrapper mockW3 = NumericalStateWrapper.bottom(man, env);

        analysis.merge(mockUnit, mockW1, mockW2, mockW3);
        Assertions.assertTrue(mockW3.get().isBottom(man), "After merge, the state should be bottom");

        mockW1 = NumericalStateWrapper.top(man, env);
        mockW2 = NumericalStateWrapper.bottom(man, env);
        analysis.merge(mockUnit, mockW1, mockW2, mockW3);
        Assertions.assertTrue(mockW3.get().isTop(man), "After merge, the state should be top when one is top");
    }

    @Test
    public void testFlowThrough() throws ApronException {
        NumericalStateWrapper inWrapper = NumericalStateWrapper.top(man, env);
        NumericalStateWrapper fallOutWrapper = NumericalStateWrapper.top(man, env);

        // Use a variable that is guaranteed to be in the environment
        Local leftOp = new JimpleLocal("frog_with_hat", RefType.v("ch.ethz.rse.Frog"));
        Value rightOp = new JimpleLocal("frog_with_pants", RefType.v("ch.ethz.rse.Frog"));
        DefinitionStmt stmt = new JAssignStmt(leftOp, rightOp);

        analysis.flowThrough(inWrapper, stmt, Collections.singletonList(fallOutWrapper), Collections.emptyList());

        // For this test, we're not focused on variable bounds; just ensuring no exceptions
        Assertions.assertNotNull(fallOutWrapper, "Fallout wrapper should not be null");
    }

    @Test
    public void testUnhandled() {
        UnsupportedOperationException thrown = Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> NumericalAnalysis.unhandled("Test task", "Test object", true),
            "Expected unhandled() to throw, but it didn't"
        );

        Assertions.assertTrue(thrown.getMessage().contains("Test task: Can't handle Test object of type java.lang.String"));
    }
}
