/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.refactoring.makeFunctionTopLevel.PyMakeFunctionTopLevelRefactoring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Mikhail Golubev
 */
public class PyMakeFunctionTopLevelTest extends PyTestCase {

  public void doTest(boolean enabled, @Nullable String message) {
    myFixture.configureByFile(getTestName(true) + ".py");
    final PyMakeFunctionTopLevelRefactoring action = new PyMakeFunctionTopLevelRefactoring();
    // Similar to com.intellij.testFramework.fixtures.CodeInsightTestFixture.testAction()
    final TestActionEvent event = new TestActionEvent(action);
    action.beforeActionPerformedUpdate(event);
    assertEquals(enabled, event.getPresentation().isEnabledAndVisible());
    if (enabled) {
      try {
        action.actionPerformed(event);
        myFixture.checkResultByFile(getTestName(true) + ".after.py");
      }
      catch (IncorrectOperationException e) {
        if (message == null) {
          fail("Refactoring failed unexpectedly with message: " + e.getMessage());
        }
        assertEquals(message, e.getMessage());
      }
    }
  }

  private void doMultiFileTest() throws IOException {
    final String rootBeforePath = getTestName(true) + "/before";
    final String rootAfterPath = getTestName(true) + "/after";
    final VirtualFile copiedDirectory = myFixture.copyDirectoryToProject(rootBeforePath, "");
    myFixture.configureByFile("main.py");
    myFixture.testAction(new PyMakeFunctionTopLevelRefactoring());
    PlatformTestUtil.assertDirectoriesEqual(getVirtualFileByName(getTestDataPath() + rootAfterPath), copiedDirectory);
  }

  private void doTestSuccess() {
    doTest(true, null);
  }

  private void doTestFailure(@NotNull String message) {
    doTest(true, message);
  }

  private static boolean isActionEnabled() {
    final PyMakeFunctionTopLevelRefactoring action = new PyMakeFunctionTopLevelRefactoring();
    final TestActionEvent event = new TestActionEvent(action);
    action.beforeActionPerformedUpdate(event);
    return event.getPresentation().isEnabled();
  }

  // PY-6637
  public void testLocalFunctionSimple() {
    doTestSuccess();
  }

  // PY-6637
  public void testRefactoringAvailability() {
    myFixture.configureByFile(getTestName(true) + ".py");

    final PsiFile file = myFixture.getFile();
    moveByText("func");
    assertFalse(isActionEnabled());
    moveByText("local");
    assertTrue(isActionEnabled());

    // move to "def" keyword
    myFixture.getEditor().getCaretModel().moveCaretRelatively(-3, 0, false, false, false);
    final PsiElement tokenAtCaret = file.findElementAt(myFixture.getCaretOffset());
    assertNotNull(tokenAtCaret);
    assertEquals(tokenAtCaret.getNode().getElementType(), PyTokenTypes.DEF_KEYWORD);
    assertTrue(isActionEnabled());

    moveByText("method");
    assertTrue(isActionEnabled());

    moveByText("static_method");
    assertFalse(isActionEnabled());
    moveByText("class_method");
    assertFalse(isActionEnabled());

    // Overridden method
    moveByText("overridden_method");
    assertFalse(isActionEnabled());

    // Overriding method
    moveByText("upper");
    assertFalse(isActionEnabled());

    moveByText("property");
    assertFalse(isActionEnabled());
    moveByText("__magic__");
    assertFalse(isActionEnabled());
  }

  // PY-6637
  public void testLocalFunctionNonlocalReferenceToOuterScope() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      @Override
      public void run() {
        doTestFailure(PyBundle.message("refactoring.make.function.top.level.error.nonlocal.writes"));
      }
    });
  }

  // PY-6637
  public void testLocalFunctionNonlocalReferencesInInnerFunction() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      @Override
      public void run() {
        doTestSuccess();
      }
    });
  }

  // PY-6637
  public void testLocalFunctionReferenceToSelf() {
    doTestFailure(PyBundle.message("refactoring.make.function.top.level.error.self.reads"));
  }

  public void testMethodNonlocalReferenceToOuterScope() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      @Override
      public void run() {
        doTestFailure(PyBundle.message("refactoring.make.function.top.level.error.nonlocal.writes"));
      }
    });
  }

  public void testMethodOuterScopeReads() {
    doTestFailure(PyBundle.message("refactoring.make.function.top.level.error.outer.scope.reads"));
  }

  public void testMethodOtherMethodCalls() {
    doTestFailure(PyBundle.message("refactoring.make.function.top.level.error.method.calls"));
  }

  public void testMethodAttributeWrites() {
    doTestFailure(PyBundle.message("refactoring.make.function.top.level.error.attribute.writes"));
  }

  public void testMethodReadPrivateAttributes() {
    doTestFailure(PyBundle.message("refactoring.make.function.top.level.error.private.attributes"));
  }

  public void testMethodSelfUsedAsOperand() {
    doTestFailure(PyBundle.message("refactoring.make.function.top.level.error.special.usage.of.self"));
  }

  public void testMethodOverriddenSelf() {
    doTestFailure(PyBundle.message("refactoring.make.function.top.level.error.special.usage.of.self"));
  }

  public void testMethodSingleAttributeRead() {
    doTestSuccess();
  }

  public void testMethodMultipleAttributesReadReferenceQualifier() {
    doTestSuccess();
  }

  public void testMethodMultipleAttributesConstructorQualifier() {
    doTestSuccess();
  }

  public void testMethodImportUpdates() throws IOException {
    doMultiFileTest();
  }

  public void testMethodCalledViaClass() {
    doTestSuccess();
  }

  public void testMethodUniqueNameOfExtractedQualifier() {
    doTestSuccess();
  }

  public void testMethodUniqueParamNames() {
    doTestSuccess();
  }

  public void testRecursiveMethod() {
    doTestSuccess();
  }

  public void testRecursiveLocalFunction() {
    doTestSuccess();
  }

  public void testMethodNoNewParams() {
    doTestSuccess();
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/makeFunctionTopLevel/";
  }
}
