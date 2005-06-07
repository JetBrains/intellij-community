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
    for (PsiMethod superMethod : superMethods) {
      if (!superMethod.hasModifierProperty(PsiModifier.ABSTRACT)
          && new AddAnnotationAction(ourFQName, method).isAvailable(project, editor, file)) {
        return true;
      }
    }

    return false;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiMethod method = findMethod(file, editor.getCaretModel().getOffset());
    new AddAnnotationAction(ourFQName, method).invoke(project, editor, file);
  }

  private static PsiMethod findMethod(PsiFile file, int offset) {
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
