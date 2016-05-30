/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.debugger.PySignatureCacheManagerImpl;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

import java.io.IOException;

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
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      doIntentionTest(PyBundle.message("INTN.annotate.types"), getTestName(true) + ".py", "foo_decl.py");
      myFixture.checkResultByFile("foo_decl.py", "foo_decl_after.py", false);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testTypeComment() {
    doTest(PyBundle.message("INTN.annotate.types"), LanguageLevel.PYTHON27);
  }

  public void testImportDict() throws IOException {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      final String testFileName = getTestName(true);
      myFixture.configureByFile(testFileName + ".py");

      String signature = PySignatureCacheManager.signatureToString(
        new PySignature(myFixture.getFile().getVirtualFile().getCanonicalPath(), "get_dict").addReturnType("Dict[int, str]"));
      PySignatureCacheManagerImpl.CALL_SIGNATURES_ATTRIBUTE.writeAttributeBytes(myFixture.getFile().getVirtualFile(),
                                                                                signature.getBytes());

      final IntentionAction intentionAction = myFixture.findSingleIntention(PyBundle.message("INTN.annotate.types"));
      assertNotNull(intentionAction);
      myFixture.launchAction(intentionAction);
      myFixture.checkResultByFile(testFileName + "_after.py", true);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }

    doTest();
  }
  
  
  private void doTest() {
    doTest(PyBundle.message("INTN.annotate.types"), LanguageLevel.PYTHON30);
  }
}
