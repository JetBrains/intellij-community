package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

public class MoveToPackageFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MoveToPackageFix");
  private PsiFile myFile;
  private PsiPackage myTargetPackage;

  public MoveToPackageFix(PsiFile file, PsiPackage targetPackage) {
    myFile = file;
    myTargetPackage = targetPackage;
  }

  public String getText() {
    final String text = MessageFormat.format("Move to package ''{0}''",
        new Object[]{
          myTargetPackage.getQualifiedName(),
        });
    return text;
  }

  public String getFamilyName() {
    return "Move Class to Package";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myFile != null
        && myFile.isValid()
        && myFile.getManager().isInProject(myFile)
        && myFile instanceof PsiJavaFile
        && ((PsiJavaFile) myFile).getClasses().length != 0
        && myTargetPackage != null
        && myTargetPackage.isValid()
        ;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myFile)) return;

    try {
      String packageName = myTargetPackage.getQualifiedName();
      final PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(project, packageName, null, true);

      if (directory == null) {
        return;
      }
      final String error = RefactoringMessageUtil.checkCanCreateFile(directory, myFile.getName());
      if (error != null) {
        Messages.showMessageDialog(project, error, "Error", Messages.getErrorIcon());
        return;
      }
      new MoveClassesOrPackagesProcessor(
              project,
              new PsiElement[]{((PsiJavaFile) myFile).getClasses()[0]},
              new SingleSourceRootMoveDestination(PackageWrapper.create(directory.getPackage()), directory), false,
              false,
              false,
              null,
              new Runnable() { public void run() { /* do nothing */ } }
      ).run(null);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }


}
