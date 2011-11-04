package com.jetbrains.python.codeInsight.testIntegration;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

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

              String dirName = dialog.getTargetDir();
              Properties defaultProperties = FileTemplateManager.getInstance().getDefaultProperties();
              Properties properties = new Properties(defaultProperties);
              properties.setProperty(FileTemplate.ATTRIBUTE_NAME, dirName);

              VirtualFile templatesFolder = VfsUtil.createDirectoryIfMissing(dialog.getTargetDir());
              PsiDirectory dir = PsiManager.getInstance(project).findDirectory(templatesFolder);
              String fileName = dialog.getFileName();
              if (!fileName.endsWith(".py"))
                fileName = fileName + "." + PythonFileType.INSTANCE.getDefaultExtension();

              VirtualFile file = LocalFileSystem.getInstance().findFileByPath(dialog.getTargetDir() + "/" +dialog.getFileName());

              StringBuilder fileText = new StringBuilder();
              fileText.append("class ").append(dialog.getClassName()).append("(TestCase):\n  ");
              List<String> methods = dialog.getMethods();
              if (methods.size() == 0)
                fileText.append("pass\n");

              for (String method : methods)
                fileText.append("def ").append(method).append("(self):\n    self.fail()\n\n");

              PsiFile psiFile = null;
              if (file != null) {
                psiFile = PsiManager.getInstance(project).findFile(file);
                AddImportHelper.addImportFrom(psiFile, "unittest", "TestCase", null, AddImportHelper.ImportPriority.BUILTIN);

                PyElement e = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.forElement(psiFile), PyClass.class,
                                                             fileText.toString());
                psiFile.addAfter(e, psiFile.getLastChild());
              }
              else {
                String importString = "from unittest import TestCase\n\n";

                psiFile = PsiFileFactory.getInstance(project).createFileFromText(fileName, importString+fileText.toString());
                dir.add(psiFile);
              }
              final VirtualFile virtualFile = psiFile.getVirtualFile();
              if (virtualFile != null) {
                FileEditorManager.getInstance(dir.getProject()).openFile(virtualFile, true);
              }
              return psiFile;
            }
            catch (IncorrectOperationException e) {
              LOG.warn(e);
              return null;
            }
            catch (IOException e) {
              LOG.warn(e);
              return null;
            }
          }
        });
      }
    });
  }
}