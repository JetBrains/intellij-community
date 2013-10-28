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
package com.jetbrains.python.codeInsight.testIntegration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testIntegration.TestCreator;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.*;

import java.util.List;

/**
 * User: catherine
 */
public class PyTestCreator implements TestCreator {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.codeInsight.testIntegration.PyTestCreator");

  @Override
  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    CreateTestAction action = new CreateTestAction();
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element != null)
      return action.isAvailable(project, editor, element);
    return false;
  }

  public void createTest(Project project, Editor editor, PsiFile file) {
    try {
      CreateTestAction action = new CreateTestAction();
      PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      if (action.isAvailable(project, editor, element)) {
        action.invoke(project, editor, file.getContainingFile());
      }
    }
    catch (IncorrectOperationException e) {
      LOG.warn(e);
    }
  }

  public static PsiFile generateTest(final Project project, final CreateTestDialog dialog) {
    return PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Computable<PsiFile>() {
      public PsiFile compute() {
        return ApplicationManager.getApplication().runWriteAction(new Computable<PsiFile>() {
          public PsiFile compute() {
            try {
              IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

              String fileName = dialog.getFileName();
              if (!fileName.endsWith(".py"))
                fileName = fileName + "." + PythonFileType.INSTANCE.getDefaultExtension();

              StringBuilder fileText = new StringBuilder();
              fileText.append("class ").append(dialog.getClassName()).append("(TestCase):\n  ");
              List<String> methods = dialog.getMethods();
              if (methods.size() == 0)
                fileText.append("pass\n");

              for (String method : methods) {
                fileText.append("def ").append(method).append("(self):\n    self.fail()\n\n  ");
              }

              PsiFile psiFile = PyUtil.getOrCreateFile(
                dialog.getTargetDir() + "/" + fileName, project);
              AddImportHelper.addImportFrom(psiFile, null, "unittest", "TestCase", null, AddImportHelper.ImportPriority.BUILTIN);

              PyElement createdClass = PyElementGenerator.getInstance(project).createFromText(
                LanguageLevel.forElement(psiFile), PyClass.class,
                                                           fileText.toString());
              createdClass = (PyElement)psiFile.addAfter(createdClass, psiFile.getLastChild());

              CodeStyleManager.getInstance(project).reformat(psiFile);
              createdClass.navigate(false);
              return psiFile;
            }
            catch (IncorrectOperationException e) {
              LOG.warn(e);
              return null;
            }
          }
        });
      }
    });
  }
}
