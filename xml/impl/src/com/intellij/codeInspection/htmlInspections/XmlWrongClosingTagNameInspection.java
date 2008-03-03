/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class XmlWrongClosingTagNameInspection extends HtmlLocalInspectionTool {

  @Nls
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("xml.inspection.wrong.closing.tag");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "XmlWrongClosingTagName";
  }

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.XML_INSPECTIONS;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  protected void checkTag(@NotNull final XmlTag tag, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    final XmlToken start = XmlTagUtil.getStartTagNameElement(tag);
    XmlToken endTagName = XmlTagUtil.getEndTagNameElement(tag);
    if (endTagName != null && !(tag instanceof HtmlTag) && !tag.getName().equals(endTagName.getText())) {
      registerProblem(holder, tag, start, endTagName);
    } else if (endTagName == null && !(tag instanceof HtmlTag && HtmlUtil.isSingleHtmlTag(tag.getName()))) {
      final PsiErrorElement errorElement = PsiTreeUtil.getChildOfType(tag, PsiErrorElement.class);
      endTagName = findEndTagName(errorElement);
      if (endTagName != null) {
        registerProblem(holder, tag, start, endTagName);
      }
    }
  }

  private static void registerProblem(@NotNull final ProblemsHolder holder, @NotNull final XmlTag tag, @Nullable final XmlToken start, @NotNull final XmlToken end) {
    final String tagName = (tag instanceof HtmlTag) ? tag.getName().toLowerCase() : tag.getName();
    final String endTokenText = (tag instanceof HtmlTag) ? end.getText().toLowerCase() : end.getText();

    final RenameTagBeginOrEndIntentionAction renameEndAction = new RenameTagBeginOrEndIntentionAction(tagName, endTokenText, false);
    final RenameTagBeginOrEndIntentionAction renameStartAction = new RenameTagBeginOrEndIntentionAction(endTokenText, tagName, true);

    if (start != null) {
      holder.registerProblem(start, XmlErrorMessages.message("tag.has.wrong.closing.tag.name"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                             renameEndAction, renameStartAction);
    }
    
    holder.registerProblem(end, XmlErrorMessages.message("wrong.closing.tag.name"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                           new RemoveExtraClosingTagIntentionAction(), renameEndAction, renameStartAction);
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
