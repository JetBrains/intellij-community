package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

public class MakeClassInterfaceFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MakeClassInterfaceFix");

  private final PsiClass myClass;

  public MakeClassInterfaceFix(PsiClass aClass) {
    myClass = aClass;
  }

  public String getText() {
    String text = MessageFormat.format("Make ''{0}'' an interface",
        new Object[]{myClass.getName(),
        });
    return text;
  }

  public String getFamilyName() {
    return "Make Class an Interface";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myClass.isValid() && myClass.getManager().isInProject(myClass);
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.preparePsiElementForWrite(myClass)) return;

    try {
      PsiJavaCodeReferenceElement[] referenceElements = myClass.getExtendsList().getReferenceElements();
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        referenceElement.delete();
      }
      convertToInterface(myClass);
      QuickFixAction.markDocumentForUndo(file);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void convertToInterface(PsiClass aClass) throws IncorrectOperationException {
    PsiElement child = aClass.getFirstChild(); //This is "class" keyword
    PsiElementFactory factory = myClass.getManager().getElementFactory();
    child.replace(factory.createKeyword(PsiKeyword.INTERFACE));
  }

  public boolean startInWriteAction() {
    return true;
  }
}
