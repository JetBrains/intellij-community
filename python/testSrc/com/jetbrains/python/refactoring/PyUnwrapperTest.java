/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.unwrap.UnwrapHandler;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

import java.util.List;

/**
 * User : ktisha
 */
public class PyUnwrapperTest extends PyTestCase {

  public void testIfUnwrap() {doTest();}
  public void testIfUnwrapEmpty() {doNegativeTest();}
  public void testIfUnwrapMultipleStatements() {doTest();}
  public void testWhileUnwrap() {doTest();}
  public void testWhileUnwrapEmpty() {doNegativeTest();}
  public void testWhileUnwrapMultipleStatements() {doTest();}
  public void testWhileElseUnwrap() {doTest();}

  public void testIfWithElseUnwrap() {doTest();}
  public void testIfInWhileUnwrap() {doTest();}
  public void testWhileInIfUnwrap() {doTest();}
  public void testIfInIfUnwrap() {doTest();}
  public void testWhileInWhileUnwrap() {doTest();}

  public void testElseInIfUnwrap() {doTest(1);}
  public void testElseInIfDelete() {doTest();}
  public void testInnerElseUnwrap() {doTest(1);}

  public void testElIfUnwrap() {doTest();}
  public void testElIfDelete() {doTest(1);}

  public void testTryUnwrap() {doTest();}
  public void testTryFinallyUnwrap() {doTest();}
  public void testTryElseFinallyUnwrap() {doTest();}

  public void testForUnwrap() {doTest();}
  public void testForElseUnwrap() {doTest();}

  public void testWithUnwrap() {doTest(LanguageLevel.PYTHON32);}

  public void testEndOfStatementUnwrap() {doTest();}
  public void testEndOfStatementNextLineUnwrap() {doNegativeTest();}

  public void testIfInElifBranchUnwrap() {doNegativeTest(PyBundle.message("unwrap.if"));}

  public void testWhitespaceAtCaretUnwrap() {doTest();}
  public void testEmptyLineAtCaretUnwrap() {doTest();}

  private void doTest() {
    doTest(0);
  }

  private void doTest(LanguageLevel languageLevel) {
    setLanguageLevel(languageLevel);
    try {
      doTest(0);
    }
    finally {
      setLanguageLevel(null);
    }
  }

  private void doTest(final int option) {
    String before = "refactoring/unwrap/" + getTestName(true) + "_before.py";
    String after = "refactoring/unwrap/" + getTestName(true) + "_after.py";
    myFixture.configureByFile(before);
    UnwrapHandler h = new UnwrapHandler() {
      @Override
      protected void selectOption(List<AnAction> options, Editor editor, PsiFile file) {
        assertTrue("No available options to unwrap", !options.isEmpty());
        options.get(option).actionPerformed(null);
      }
    };
    h.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());

    myFixture.checkResultByFile(after, true);
   }


  private void doNegativeTest() {
    String before = "refactoring/unwrap/" + getTestName(true) + "_before.py";
    myFixture.configureByFile(before);
    UnwrapHandler h = new UnwrapHandler() {
      @Override
      protected void selectOption(List<AnAction> options, Editor editor, PsiFile file) {
        assertEmpty(options);
      }
    };
    h.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
  }

  private void doNegativeTest(final String optionName) {
    String before = "refactoring/unwrap/" + getTestName(true) + "_before.py";
    myFixture.configureByFile(before);
    UnwrapHandler h = new UnwrapHandler() {
      @Override
      protected void selectOption(List<AnAction> options, Editor editor, PsiFile file) {
        for (AnAction option : options) {
          assertFalse("\"" + optionName  + "\" is available to unwrap ", option.toString().contains(optionName));
        }
      }
    };
    h.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
  }

}
