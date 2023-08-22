// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.quickFixes;

import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.PyAsyncCallInspection;

public class PyAsyncCallQuickFixTest extends PyQuickFixTestCase {

  // PY-17292
  public void testAddAwaitBeforeCall() {
    doQuickFixTest(PyAsyncCallInspection.class, PyPsiBundle.message("QFIX.coroutine.is.not.awaited"));
  }

  // PY-17292
  public void testAddYieldFromBeforeCall() {
    doMultifilesTest(PyAsyncCallInspection.class, PyPsiBundle.message("QFIX.coroutine.is.not.awaited"),
                     new String[]{"addYieldFromBeforeCall.py", "asyncio/__init__.py", "asyncio/coroutines.py"});
  }
}
