package com.intellij.lang.xml;

import com.intellij.codeInsight.unwrap.Unwrapper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.xml.XmlBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.ASTNode;

import java.util.Set;
import java.util.List;

public class XmlEnclosingTagUnwrapper implements Unwrapper {
  public boolean isApplicableTo(PsiElement e) {
    return true;
  }

  public void collectElementsToIgnore(PsiElement element, Set<PsiElement> result) {
  }

  public String getDescription(PsiElement e) {
    return XmlBundle.message("unwrap.enclosing.tag.name.action.name");
  }

  public TextRange collectTextRanges(PsiElement e, List<TextRange> toExtract) {
    return e.getTextRange();
  }

  public void unwrap(Editor editor, PsiElement element) throws IncorrectOperationException {
    final TextRange range = element.getTextRange();
    final ASTNode startTagNameEnd = XmlChildRole.START_TAG_END_FINDER.findChild(element.getNode());
    final ASTNode endTagNameStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(element.getNode());

    if (endTagNameStart != null) {
      editor.getDocument().replaceString(endTagNameStart.getTextRange().getStartOffset(), range.getEndOffset(), "");
      editor.getDocument().replaceString(range.getStartOffset(), startTagNameEnd.getTextRange().getEndOffset(), "");
    }
    else {
      editor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), "");
    }
  }
}
