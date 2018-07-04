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

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.refactoring.move.PyMoveSymbolDelegate;
import com.jetbrains.python.refactoring.move.makeFunctionTopLevel.PyMakeLocalFunctionTopLevelProcessor;
import com.jetbrains.python.refactoring.move.makeFunctionTopLevel.PyMakeMethodTopLevelProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Mikhail Golubev
 */
public class PyMakeFunctionTopLevelTest extends PyTestCase {

  public void doTest(@Nullable String errorMessage) {
    myFixture.configureByFile(getTestName(true) + ".py");
    runRefactoring(null, errorMessage);
    if (errorMessage == null) {
      myFixture.checkResultByFile(getTestName(true) + ".after.py");
    }
  }

  private void doTestSuccess() {
    doTest(null);
  }

  private void doTestFailure(@NotNull String message) {
    doTest(message);
  }

  private void runRefactoring(@Nullable String destination, @Nullable String errorMessage) {
    final PyFunction function = assertInstanceOf(myFixture.getElementAtCaret(), PyFunction.class);
    if (destination == null) {
      destination = PyPsiUtils.getContainingFilePath(function);
    }
    else {
      final VirtualFile srcRoot = ModuleRootManager.getInstance(myFixture.getModule()).getSourceRoots()[0];
      destination = FileUtil.join(srcRoot.getPath(), destination);
    }
    assertNotNull(destination);
    final String finalDestination = destination;
    try {
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        if (function.getContainingClass() != null) {
          new PyMakeMethodTopLevelProcessor(function, finalDestination).run();
        }
        else {
          new PyMakeLocalFunctionTopLevelProcessor(function, finalDestination).run();
        }
      });
    }
    catch (IncorrectOperationException e) {
      if (errorMessage == null) {
        fail("Refactoring failed unexpectedly with message: " + e.getMessage());
      }
      assertEquals(errorMessage, e.getMessage());
    }
  }

  private void doMultiFileTest(@Nullable String destination, @Nullable String errorMessage) throws IOException {
    final String rootBeforePath = getTestName(true) + "/before";
    final String rootAfterPath = getTestName(true) + "/after";
    final VirtualFile copiedDirectory = myFixture.copyDirectoryToProject(rootBeforePath, "");
    myFixture.configureByFile("main.py");
    runRefactoring(destination, errorMessage);
    if (errorMessage == null) {
      PlatformTestUtil.assertDirectoriesEqual(getVirtualFileByName(getTestDataPath() + rootAfterPath), copiedDirectory);
    }
  }

  private boolean isActionEnabled() {
    final int offset = myFixture.getCaretOffset();
    PsiElement element = myFixture.getFile().findElementAt(offset);

    // Duplicates the logic of MoveHandler#invoke(), since it doesn't allow to check
    // whether MoveHandlerDelegate#tryMove() returns true or false without actually
    // invoking it.
    while (element != null) {
      if (PyMoveSymbolDelegate.isMovableLocalFunctionOrMethod(element)) {
        return true;
      }
      final TextRange range = element.getTextRange();
      if (range != null) {
        final int relative = offset - range.getStartOffset();
        final PsiReference reference = element.findReferenceAt(relative);
        if (reference != null) {
          final PsiElement refElement = reference.resolve();
          if (refElement != null && PyMoveSymbolDelegate.isMovableLocalFunctionOrMethod(myFixture.getElementAtCaret())) {
            return true;
          }
        }
      }
      element = element.getParent();
    }
    return false;
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
    assertEquals(PyTokenTypes.DEF_KEYWORD, tokenAtCaret.getNode().getElementType());
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
    runWithLanguageLevel(LanguageLevel.PYTHON34,
                         () -> doTestFailure(PyBundle.message("refactoring.make.function.top.level.error.nonlocal.writes")));
  }

  // PY-6637
  public void testLocalFunctionNonlocalReferencesInInnerFunction() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTestSuccess);
  }

  // PY-6637
  public void testLocalFunctionReferenceToSelf() {
    doTestFailure(PyBundle.message("refactoring.make.function.top.level.error.self.reads"));
  }

  public void testMethodNonlocalReferenceToOuterScope() {
    runWithLanguageLevel(LanguageLevel.PYTHON34,
                         () -> doTestFailure(PyBundle.message("refactoring.make.function.top.level.error.nonlocal.writes")));
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
    doMultiFileTest(null, null);
  }

  public void testMethodMoveToOtherFile() throws IOException {
    doMultiFileTest("util.py", null);
  }

  public void testLocalFunctionMoveToOtherFile() throws IOException {
    doMultiFileTest("util.py", null);
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

  public void testMethodNotImportableDestinationFile() throws IOException {
    doMultiFileTest("not-importable.py", PyBundle.message("refactoring.move.error.cannot.use.module.name.$0", "not-importable.py"));
  }

  public void testLocalFunctionNameCollision() {
    doTestFailure(PyBundle.message("refactoring.move.error.destination.file.contains.function.$0", "nested"));
  }

  public void testMethodInsertionPositionSameFileClassAndUsageNotTopLevel() {
    doTestSuccess();
  }

  public void testMethodInsertionPositionUsageInAnotherFile() throws IOException {
    doMultiFileTest("other.py", null);
  }

  public void testMethodInsertionPositionNoUsageInAnotherFile() throws IOException {
    doMultiFileTest("other.py", null);    
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/makeFunctionTopLevel/";
  }
}
