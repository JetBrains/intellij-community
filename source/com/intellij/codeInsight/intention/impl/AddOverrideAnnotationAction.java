package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class AddOverrideAnnotationAction implements IntentionAction {
  private static final String ourFQName = "java.lang.Override";

  public String getText() {
    return "Add '@Override' Annotation";
  }

  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    PsiMethod method = findMethod(file, editor.getCaretModel().getOffset());
    if (method == null) return false;
    if (method.getModifierList().findAnnotation(ourFQName) != null) return false;
    PsiMethod[] superMethods = method.findSuperMethods();
    for (int i = 0; i < superMethods.length; i++) {
      PsiMethod superMethod = superMethods[i];
      if (!superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) return true;
    }

    return false;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiManager manager = file.getManager();
    PsiElementFactory factory = manager.getElementFactory();
    PsiMethod method = findMethod(file, editor.getCaretModel().getOffset());
    PsiAnnotation annotation = factory.createAnnotationFromText("@" + ourFQName, method);
    method.getModifierList().addAfter(annotation, null);
  }

  private PsiMethod findMethod(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    PsiMethod res = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (res == null) return null;

    //Not available in method's body
    PsiCodeBlock body = res.getBody();
    if (body == null) return null;
    if (body.getTextRange().getStartOffset() <= offset) return null;

    return res;
  }

  public boolean startInWriteAction() {
    return true;
  }

}
