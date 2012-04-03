package com.jetbrains.python.codeInsight.testIntegration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.*;

import java.util.List;

/**
 * User: catherine
 */
public class PyTestGenerator {
  public PyTestGenerator() {
  }
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.codeInsight.testIntegration.PyTestGenerator");
  public PsiElement generateTest(final Project project, final CreateTestDialog dialog) {
    return PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Computable<PsiElement>() {
      public PsiElement compute() {
        return ApplicationManager.getApplication().runWriteAction(new Computable<PsiElement>() {
          public PsiElement compute() {
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