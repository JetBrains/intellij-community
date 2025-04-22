// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes;

import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.PyInconsistentReturnsInspection;

@TestDataPath("$CONTENT_ROOT/../testData/quickFixes/PyMakeReturnsExplicitFixTest/")
public class PyMakeReturnsExplicitFixTest extends PyQuickFixTestCase {

  public void testAddReturnsFromReturnStmt() {
    doQuickFixTest(PyInconsistentReturnsInspection.class, PyPsiBundle.message("QFIX.NAME.make.return.stmts.explicit"));
  }

  // PY-80493
  public void testContextManagerSuppressingException() {
    doQuickFixTest(PyInconsistentReturnsInspection.class, PyPsiBundle.message("QFIX.NAME.make.return.stmts.explicit"));
  }

  // PY-80493
  public void testContextManagerNotSuppressingException() {
    doQuickFixTest(PyInconsistentReturnsInspection.class, PyPsiBundle.message("QFIX.NAME.make.return.stmts.explicit"));
  }

  @Override
  protected void doQuickFixTest(final Class inspectionClass, final String hint) {
    final String testFileName = getTestName(true);
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFile(testFileName + ".py");
    myFixture.checkHighlighting(true, false, true);
    final var intentionAction = myFixture.findSingleIntention(hint);
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + "_after.py", true);
  }
}
