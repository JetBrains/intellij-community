// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.quickFixes;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.PyAsyncCallInspection;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.Nullable;

public class PyAsyncCallQuickFixTest extends PyQuickFixTestCase {

  // PY-17292
  public void testAddAwaitBeforeCall() {
    doQuickFixTest(PyAsyncCallInspection.class, PyAsyncCallInspection.coroutineIsNotAwaited, LanguageLevel.PYTHON35);
  }

  // PY-17292
  public void testAddYieldFromBeforeCall() {
    runWithLanguageLevel(LanguageLevel.PYTHON35,
                         () -> doMultifilesTest(PyAsyncCallInspection.class, PyAsyncCallInspection.coroutineIsNotAwaited,
                                                new String[]{"addYieldFromBeforeCall.py", "asyncio/__init__.py", "asyncio/coroutines.py"}));
  }

  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }
}
