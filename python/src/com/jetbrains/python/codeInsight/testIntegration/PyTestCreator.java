// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.testIntegration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testIntegration.TestCreator;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.testing.PythonUnitTestDetectorsBasedOnSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PyTestCreator implements TestCreator {
  private static final Logger LOG = Logger.getInstance(PyTestCreator.class);

  @Override
  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    CreateTestAction action = new CreateTestAction();
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element != null) {
      return action.isAvailable(project, editor, element);
    }
    return false;
  }

  @Override
  public void createTest(Project project, Editor editor, PsiFile file) {
    try {
      CreateTestAction action = new CreateTestAction();
      PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      if (element != null && action.isAvailable(project, editor, element)) {
        action.invoke(project, editor, file.getContainingFile());
      }
    }
    catch (IncorrectOperationException e) {
      LOG.warn(e);
    }
  }

  /**
   * Generates test, puts it into file and navigates to newly created class
   *
   * @return file with test
   */
  static PsiFile generateTestAndNavigate(@NotNull final PsiElement anchor, @NotNull final PyTestCreationModel creationModel) {
    final Project project = anchor.getProject();
    return PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(
      () -> ApplicationManager.getApplication().runWriteAction((Computable<PsiFile>)() -> {
        try {
          final PyElement testClass = generateTest(anchor, creationModel);
          testClass.navigate(false);
          return testClass.getContainingFile();
        }
        catch (IncorrectOperationException e) {
          LOG.warn(e);
          return null;
        }
      }));
  }

  /**
   * Generates test, puts it into file and returns element to navigate to
   *
   * @return newly created test class
   */
  @NotNull
  static PyElement generateTest(@NotNull final PsiElement anchor, @NotNull final PyTestCreationModel model) {
    final Project project = anchor.getProject();
    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

    String fileName = model.getFileName();
    if (!fileName.endsWith(".py")) {
      fileName = fileName + "." + PythonFileType.INSTANCE.getDefaultExtension();
    }
    final PyFile psiFile = PyClassRefactoringUtil.getOrCreateFile(model.getTargetDir() + "/" + fileName, project);

    final String className = model.getClassName();
    final List<String> methods = model.getMethods();

    final boolean classBased = !StringUtil.isEmptyOrSpaces(className);
    final LanguageLevel level = LanguageLevel.forElement(psiFile);
    final PyElementGenerator generator = PyElementGenerator.getInstance(project);

    PyElement result = psiFile;
    if (classBased) {
      final boolean unitTestClassRequired = PythonUnitTestDetectorsBasedOnSettings.isTestCaseClassRequired(anchor);
      final StringBuilder fileText = new StringBuilder();
      fileText.append("class ").append(className);
      if (unitTestClassRequired) {
        fileText.append("(TestCase)");
      }
      else if (LanguageLevel.forElement(anchor).isPython2()) {
        fileText.append("(object)");
      }
      fileText.append(":\n\t");


      if (methods.isEmpty()) {
        fileText.append("pass\n");
      }
      for (final String method : methods) {
        fileText.append("def ").append(method).append("(self):\n\t");
        if (unitTestClassRequired) {
          fileText.append("self.fail()");
        }
        else {
          fileText.append("assert False");
        }
        fileText.append("\n\n\t");
      }
      if (unitTestClassRequired) {
        AddImportHelper.addOrUpdateFromImportStatement(psiFile, "unittest", "TestCase", null, AddImportHelper.ImportPriority.BUILTIN,
                                                       null);
      }
      result = (PyElement)psiFile.addAfter(generator.createFromText(level, PyClass.class, fileText.toString()), psiFile.getLastChild());
    }
    else {
      for (final String method : methods) {
        final PyFunction fun = generator.createFromText(level, PyFunction.class, String.format("def %s():\n\tassert False\n\n", method));
        psiFile.addAfter(fun, psiFile.getLastChild());
      }
    }

    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting(psiFile.getViewProvider());
    CodeStyleManager.getInstance(project).reformat(psiFile);
    return result;
  }
}
