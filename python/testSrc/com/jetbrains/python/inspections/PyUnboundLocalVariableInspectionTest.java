package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author vlan
 */
public class PyUnboundLocalVariableInspectionTest extends PyTestCase {
  private static final String TEST_DIRECTORY = "inspections/PyUnboundLocalVariableInspection/";

  public void testSimple() {
    doTest();
  }

  // PY-1138
  public void testControlFlowInAssert() {
    doTest();
  }

  // PY-1176
  public void testLocalFunctionAndVariable() {
    doTest();
  }

  // PY-1359
  public void testUnboundLoopVariable() {
    doTest();
  }

  // PY-1408
  public void testUnboundExceptAs() {
    runWithLanguageLevel(LanguageLevel.PYTHON33, new Runnable() {
      @Override
      public void run() {
        doTest();
      }
    });
  }

  // PY-1434
  public void testClassLevelSameName() {
    doTest();
  }

  // PY-1435
  public void testInstanceFieldAndGlobal() {
    doTest();
  }

  // PY-3343
  public void testUnboundVariableFunctionCall() {
    doTest();
  }

  // PY-3407
  public void testUnboundNestedComprehension() {
    doTest();
  }

  // PY-3503
  public void testControlFlowInTryExceptFinally() {
    doTest();
  }

  // PY-3550
  public void testDefaultArgument() {
    doTest();
  }

  // PY-3583
  public void testUnboundConditionalImport() {
    doTest();
  }

  // PY-3603
  public void testUnboundNonLocal() {
    runWithLanguageLevel(LanguageLevel.PYTHON33, new Runnable() {
      @Override
      public void run() {
        doTest();
      }
    });
  }

  // PY-3671
  public void testUnboundConditionalImportAlias() {
    doTest();
  }

  // PY-3665
  public void testUnboundConditionalImportQualified() {
    doTest();
  }

  // PY-3651
  public void testUnboundAugmentedAssignment() {
    doTest();
  }

  // PY-3702
  public void testSysExit() {
    doTest();
  }

  // PY-3895
  public void testDecoratorAndParameter() {
    doTest();
  }

  // PY-4102
  public void testDefinedInTryUsedAfter() {
    doTest();
  }

  // PY-4150
  public void testParamAfterTryExcept() {
    doTest();
  }

  // PY-4151
  public void testUnboundDefinedInTryUsedAfterExcept() {
    doTest();
  }

  // PY-4152
  public void testDefinedInTryAndExcept() {
    doTest();
  }

  // PY-4157
  public void testDefinedInTryElse() {
    doTest();
  }

  // PY-4197
  public void testUnboundSwapStrUnicode() {
    doTest();
  }

  // PY-4229
  public void testInstanceAttributeOutsideClass() {
    doTest();
  }

  // PY-4297
  public void testOuterFunctionsAndSelfAttributes() {
    doTest();
  }

  // PY-4623
  public void testBuiltinAndSelfAttribute() {
    doTest();
  }

  // PY-4609
  public void testImplicitIfNotNone() {
    doTest();
  }

  // PY-4239
  public void testForBreakElse() {
    doTest();
  }

  // PY-5592
  public void testStarImportTopLevel() {
    doTest();
  }

  // PY-7966
  public void testUseAfterWithAndRaise() {
    doTest();
  }

  // PY-6114
  public void testUnboundUnreachable() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(TEST_DIRECTORY + getTestName(false) + ".py");
    myFixture.enableInspections(PyUnboundLocalVariableInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
