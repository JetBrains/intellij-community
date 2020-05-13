// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;

public class SpecifyTypeInPy3AnnotationsIntentionTest extends PyIntentionTestCase {
  public void testCaretOnDefinition() {
    doTestReturnType();
  }



  public void testCaretOnInvocation() {
    doTestReturnType();
  }

  public void testCaretOnImportedInvocation() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> {
        doIntentionTest(PyPsiBundle.message("INTN.specify.return.type.in.annotation"), getTestName(true) + ".py", "foo_decl.py");
        myFixture.checkResultByFile("foo_decl.py", "foo_decl_after.py", false);
      }
    );
  }

  public void testCaretOnParamUsage() {
    doTestParam();
  }


  private void doTestReturnType() {
    doTest(PyPsiBundle.message("INTN.specify.return.type.in.annotation"), LanguageLevel.PYTHON34);
  }


  private void doTestParam() {
    doTest(PyPsiBundle.message("INTN.specify.type.in.annotation"), LanguageLevel.PYTHON34);
  }
}
