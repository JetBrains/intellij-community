package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class AddMethodBodyFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddMethodBodyFix");

  private final PsiMethod myMethod;

  public AddMethodBodyFix(PsiMethod method) {
    myMethod = method;
  }

  public String getText() {
    return "Add Method Body";
  }

  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myMethod != null
        && myMethod.isValid()
        && myMethod.getBody() == null
        && myMethod.getContainingClass() != null
        && myMethod.getManager().isInProject(myMethod);
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myMethod.getContainingFile())) return;

    try {
      PsiMethod result = (PsiMethod) myMethod.copy();
      result.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, true);
      PsiClass dummyClass = file.getManager().getElementFactory().createClass("_Dummy_");
      PsiClass aClass = myMethod.getContainingClass();
      PsiMethod[] methods = OverrideImplementUtil.overrideOrImplementMethod(dummyClass, result, true);
      if (methods.length == 0) return;
      PsiElement newMethod = aClass.addBefore(methods[0], myMethod);
      myMethod.delete();

      GenerateMembersUtil.positionCaret(editor, newMethod, true);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
