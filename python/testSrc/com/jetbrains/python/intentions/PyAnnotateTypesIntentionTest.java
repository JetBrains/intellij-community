// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.intentions;

import com.intellij.openapi.vfs.VirtualFile;
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
      LanguageLevel.PYTHON34,
      () -> {
        doIntentionTest(PyBundle.message("INTN.add.type.hints.for.function.family"), getTestName(true) + ".py", "foo_decl.py");
        myFixture.checkResultByFile("foo_decl.py", "foo_decl_after.py", false);
      }
    );
  }

  public void testTypeComment() {
    doTest(PyBundle.message("INTN.add.type.hints.for.function.family"), LanguageLevel.PYTHON27);
  }

  public void testLibraryDefinition() {
    final String testDir = getTestName(false);
    myFixture.copyDirectoryToProject(testDir, "");
    final VirtualFile libDir = myFixture.findFileInTempDir("lib");
    runWithAdditionalClassEntryInSdkRoots(libDir, () -> {
      myFixture.configureByFile(testDir + "/main.py");
      assertEmpty(myFixture.filterAvailableIntentions(PyBundle.message("INTN.add.type.hints.for.function.family")));
    });
  }

  // PY-30713
  public void testResolveAmbiguity() {
    doNegativeTest(PyBundle.message("INTN.add.type.hints.for.function.family"));
  }

  private void doTest() {
    doTest(PyBundle.message("INTN.add.type.hints.for.function.family"), LanguageLevel.PYTHON34);
  }
}
