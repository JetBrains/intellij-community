// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author traff
 */
public class PyAnnotateTypesIntentionTest extends PyIntentionTestCase {
  public void testCaretOnDefinition() {
    doTest();
  }

  public void testCaretOnInvocation() {
    doTest();
  }

  public void testCaretOnImportedInvocation() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON30,
      () -> {
        doIntentionTest(PyBundle.message("INTN.annotate.types"), getTestName(true) + ".py", "foo_decl.py");
        myFixture.checkResultByFile("foo_decl.py", "foo_decl_after.py", false);
      }
    );
  }

  public void testTypeComment() {
    doTest(PyBundle.message("INTN.annotate.types"), LanguageLevel.PYTHON27);
  }
  
  private void doTest() {
    doTest(PyBundle.message("INTN.annotate.types"), LanguageLevel.PYTHON30);
  }
}
