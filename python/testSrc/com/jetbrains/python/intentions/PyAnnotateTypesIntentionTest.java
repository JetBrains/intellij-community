// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;

public class PyAnnotateTypesIntentionTest extends PyIntentionTestCase {
  public void testCaretOnDefinition() {
    doTest();
  }

  public void testCaretOnInvocation() {
    doTest();
  }

  public void testCaretOnImportedInvocation() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> {
        doIntentionTest(PyPsiBundle.message("INTN.add.type.hints.for.function.family"), getTestName(true) + ".py", "foo_decl.py");
        myFixture.checkResultByFile("foo_decl.py", "foo_decl_after.py", false);
      }
    );
  }

  public void testTypeComment() {
    doTest(PyPsiBundle.message("INTN.add.type.hints.for.function.family"), LanguageLevel.PYTHON27);
  }

  public void testLibraryDefinition() {
    final String testDir = getTestName(false);
    myFixture.copyDirectoryToProject(testDir, "");
    runWithAdditionalClassEntryInSdkRoots(testDir + "/lib", () -> {
      myFixture.configureByFile(testDir + "/main.py");
      assertEmpty(myFixture.filterAvailableIntentions(PyPsiBundle.message("INTN.add.type.hints.for.function.family")));
    });
  }

  // PY-30713
  public void testResolveAmbiguity() {
    doNegativeTest(PyPsiBundle.message("INTN.add.type.hints.for.function.family"));
  }

  // PY-30825
  public void testMethodAfterConstructorCall() {
    doIntentionTest(PyPsiBundle.message("INTN.add.type.hints.for.function", "method"));
  }

  private void doTest() {
    doTest(PyPsiBundle.message("INTN.add.type.hints.for.function.family"), LanguageLevel.PYTHON34);
  }
}
