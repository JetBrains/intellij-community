package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

public class AdjustPackageNameFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AdjustPackageNameFix");
  private final PsiJavaFile myFile;
  private PsiPackageStatement myStatement;
  private PsiPackage myTargetPackage;

  public AdjustPackageNameFix(PsiJavaFile file, PsiPackageStatement statement, PsiPackage targetPackage) {
    myFile = file;
    myStatement = statement;
    myTargetPackage = targetPackage;
  }

  public String getText() {
    final String text = MessageFormat.format("Set package name to ''{0}''",
        new Object[]{
          myTargetPackage.getQualifiedName(),
        });
    return text;
  }

  public String getFamilyName() {
    return "Adjust Package Name";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myFile != null
        && myFile.isValid()
        && myFile.getManager().isInProject(myFile)
        && myTargetPackage != null
        && myTargetPackage.isValid()
        && (myStatement == null || myStatement.isValid())
        ;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myFile)) return;

    try {
      final PsiElementFactory factory = myFile.getManager().getElementFactory();
      if (myTargetPackage.getQualifiedName().equals("")) {
        if (myStatement != null) {
          myStatement.delete();
        }
      }
      else {
        if (myStatement != null) {
          final PsiJavaCodeReferenceElement packageReferenceElement = factory.createPackageReferenceElement(myTargetPackage);
          myStatement.getPackageReference().replace(packageReferenceElement);
        }
        else {
          final PsiPackageStatement packageStatement = factory.createPackageStatement(myTargetPackage.getQualifiedName());
          myFile.addAfter(packageStatement, null);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }


}
