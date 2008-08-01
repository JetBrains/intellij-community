/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class XmlWrongClosingTagNameInspection implements Annotator {

  public void annotate(final PsiElement psiElement, final AnnotationHolder holder) {
    if (psiElement instanceof XmlToken) {
      final PsiElement parent = psiElement.getParent();
      if (parent instanceof XmlTag) {
        final XmlTag tag = (XmlTag)parent;
        final XmlToken start = XmlTagUtil.getStartTagNameElement(tag);
        XmlToken endTagName = XmlTagUtil.getEndTagNameElement(tag);
        if (endTagName != null && !(tag instanceof HtmlTag) && !tag.getName().equals(endTagName.getText())) {
          registerProblem(holder, tag, start, endTagName);
        }
        else if (endTagName == null && !(tag instanceof HtmlTag && HtmlUtil.isSingleHtmlTag(tag.getName()))) {
          final PsiErrorElement errorElement = PsiTreeUtil.getChildOfType(tag, PsiErrorElement.class);
          endTagName = findEndTagName(errorElement);
          if (endTagName != null) {
            registerProblem(holder, tag, start, endTagName);
          }
        }
      }
    }
  }

  private static void registerProblem(@NotNull final AnnotationHolder holder,
                                      @NotNull final XmlTag tag,
                                      @Nullable final XmlToken start,
                                      @NotNull final XmlToken end) {
    final String tagName = (tag instanceof HtmlTag) ? tag.getName().toLowerCase() : tag.getName();
    final String endTokenText = (tag instanceof HtmlTag) ? end.getText().toLowerCase() : end.getText();

    final RenameTagBeginOrEndIntentionAction renameEndAction = new RenameTagBeginOrEndIntentionAction(tagName, endTokenText, false);
    final RenameTagBeginOrEndIntentionAction renameStartAction = new RenameTagBeginOrEndIntentionAction(endTokenText, tagName, true);

    if (start != null) {
      final Annotation annotation = holder.createErrorAnnotation(start, XmlErrorMessages.message("tag.has.wrong.closing.tag.name"));
      annotation.registerFix(renameEndAction);
      annotation.registerFix(renameStartAction);
    }

    final Annotation annotation = holder.createErrorAnnotation(end, XmlErrorMessages.message("wrong.closing.tag.name"));
    annotation.registerFix(new RemoveExtraClosingTagIntentionAction());
    annotation.registerFix(renameEndAction);
    annotation.registerFix(renameStartAction);
  }

  @Nullable
  static XmlToken findEndTagName(@Nullable final PsiErrorElement element) {
    if (element == null) return null;

    final ASTNode astNode = element.getNode();
    if (astNode == null) return null;

    ASTNode current = astNode.getLastChildNode();
    ASTNode prev = current;

    while (current != null) {
      final IElementType elementType = prev.getElementType();

      if ((elementType == XmlElementType.XML_NAME || elementType == XmlElementType.XML_TAG_NAME) &&
          current.getElementType() == XmlElementType.XML_END_TAG_START) {
        return (XmlToken)prev.getPsi();
      }

      prev = current;
      current = current.getTreePrev();
    }

    return null;
  }
}
