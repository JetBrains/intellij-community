package com.intellij.ide;

import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

/**
 * @author yole
 */
public class JavaFilePasteProvider implements PasteProvider {
  public void performPaste(final DataContext dataContext) {
    final Project project = DataKeys.PROJECT.getData(dataContext);
    final IdeView ideView = DataKeys.IDE_VIEW.getData(dataContext);
    if (project == null || ideView == null) return;
    final PsiJavaFile javaFile = createJavaFileFromClipboardContent(project);
    if (javaFile == null) return;
    final PsiClass[] classes = javaFile.getClasses();
    if (classes.length != 1) return;
    final PsiDirectory targetDir = ideView.getOrChooseDirectory();
    if (targetDir == null) return;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiJavaFile file;
        try {
          file = (PsiJavaFile)targetDir.createFile(classes[0].getName() + ".java");
        }
        catch (IncorrectOperationException e) {
          return;
        }
        final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        document.setText(javaFile.getText());
        PsiDocumentManager.getInstance(project).commitDocument(document);
        updatePackageStatement(file, targetDir);
        new OpenFileDescriptor(project, file.getVirtualFile()).navigate(true);
      }
    });
  }

  private static void updatePackageStatement(final PsiJavaFile javaFile, final PsiDirectory targetDir) {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(targetDir);
    if (aPackage == null) return;
    final PsiPackageStatement oldStatement = javaFile.getPackageStatement();
    final Project project = javaFile.getProject();
    if ((oldStatement != null && !oldStatement.getPackageName().equals(aPackage.getQualifiedName()) ||
        (oldStatement == null && aPackage.getQualifiedName().length() > 0))) {
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        public void run() {
          try {
            PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            final PsiPackageStatement newStatement = factory.createPackageStatement(aPackage.getQualifiedName());
            if (oldStatement != null) {
              oldStatement.replace(newStatement);
            }
            else {
              final PsiElement addedStatement = javaFile.addAfter(newStatement, null);
              final TextRange textRange = addedStatement.getTextRange();
              // ensure line break is added after the statement
              CodeStyleManager.getInstance(project).reformatRange(javaFile, textRange.getStartOffset(), textRange.getEndOffset()+1);
            }
          }
          catch (IncorrectOperationException e) {
            // ignore
          }
        }
      }, "Updating package statement", null);
    }
  }

  public boolean isPastePossible(final DataContext dataContext) {
    return true;
  }

  public boolean isPasteEnabled(final DataContext dataContext) {
    final Project project = DataKeys.PROJECT.getData(dataContext);
    final IdeView ideView = DataKeys.IDE_VIEW.getData(dataContext);
    if (project == null || ideView == null || ideView.getDirectories().length == 0) {
      return false;
    }
    PsiJavaFile file = createJavaFileFromClipboardContent(project);
    return file != null && file.getClasses().length == 1;
  }

  @Nullable
  private static PsiJavaFile createJavaFileFromClipboardContent(final Project project) {
    PsiJavaFile file = null;
    Transferable content = CopyPasteManager.getInstance().getContents();
    if (content != null) {
      String text = null;
      try {
        text = (String)content.getTransferData(DataFlavor.stringFlavor);
      }
      catch (Exception e) {
        // ignore;
      }
      if (text != null) {
        file = (PsiJavaFile) PsiFileFactory.getInstance(project).createFileFromText("A.java", StdLanguages.JAVA, text);
      }
    }
    return file;
  }
}
