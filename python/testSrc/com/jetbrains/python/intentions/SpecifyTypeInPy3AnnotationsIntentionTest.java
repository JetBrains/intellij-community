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

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

/**
 * @author traff
 */
public class SpecifyTypeInPy3AnnotationsIntentionTest extends PyIntentionTestCase {
  public void testCaretOnDefinition() {
    doTestReturnType();
  }



  public void testCaretOnInvocation() {
    doTestReturnType();
  }

  public void testCaretOnImportedInvocation() {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      doIntentionTest(PyBundle.message("INTN.specify.return.type.in.annotation"), getTestName(true) + ".py", "foo_decl.py");
      myFixture.checkResultByFile("foo_decl.py", "foo_decl_after.py", false);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testCaretOnParamUsage() {
    doTestParam();
  }


  private void doTestReturnType() {
    doTest(PyBundle.message("INTN.specify.return.type.in.annotation"), LanguageLevel.PYTHON30);
  }


  private void doTestParam() {
    doTest(PyBundle.message("INTN.specify.type.in.annotation"), LanguageLevel.PYTHON30);
  }
}
