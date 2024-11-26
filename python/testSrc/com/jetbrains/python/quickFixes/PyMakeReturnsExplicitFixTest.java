// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes;

import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.PyTypeCheckerInspection;

@TestDataPath("$CONTENT_ROOT/../testData/quickFixes/PyMakeReturnsExplicitFixTest/")
public class PyMakeReturnsExplicitFixTest extends PyQuickFixTestCase {

  public void testAddReturnsFromReturnStmt() {
    doQuickFixTest(PyTypeCheckerInspection.class, PyPsiBundle.message("QFIX.NAME.make.return.stmts.explicit"));
  }
}
