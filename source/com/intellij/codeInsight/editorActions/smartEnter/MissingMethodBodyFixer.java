package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 7:24:03 PM
 * To change this template use Options | File Templates.
 */
public class MissingMethodBodyFixer implements Fixer {
  public void apply(Editor editor, SmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiMethod)) return;
    PsiMethod method = (PsiMethod) psiElement;
    if (method.getContainingClass().isInterface() || method.hasModifierProperty(PsiModifier.ABSTRACT)) return;
    final PsiCodeBlock body = method.getBody();
    if (body != null) return;
    final Document doc = editor.getDocument();
    int endOffset = method.getTextRange().getEndOffset();
    if (StringUtil.endsWithChar(method.getText(), ';')) {
      doc.deleteString(endOffset - 1, endOffset);
      endOffset--;
    }
    doc.insertString(endOffset, "{\n}");
  }
}
