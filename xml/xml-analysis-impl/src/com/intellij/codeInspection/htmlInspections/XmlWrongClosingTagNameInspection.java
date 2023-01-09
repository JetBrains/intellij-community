// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.htmlInspections;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.psi.XmlPsiBundle;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class XmlWrongClosingTagNameInspection implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (psiElement instanceof XmlToken) {
      PsiElement parent = psiElement.getParent();
      if (parent instanceof XmlTag) {
        XmlTag tag = (XmlTag)parent;
        XmlToken start = XmlTagUtil.getStartTagNameElement(tag);
        XmlToken endTagName = XmlTagUtil.getEndTagNameElement(tag);
        if (start == psiElement) {
          if (endTagName != null && !(tag instanceof HtmlTag) && !tag.getName().equals(endTagName.getText())) {
            registerProblemStart(holder, tag, start, endTagName);
          }
          else if (endTagName == null && !(tag instanceof HtmlTag && HtmlUtil.isSingleHtmlTag(tag, true))) {
            PsiErrorElement errorElement = PsiTreeUtil.getChildOfType(tag, PsiErrorElement.class);
            endTagName = findEndTagName(errorElement);
            if (endTagName != null) {
              registerProblemStart(holder, tag, start, endTagName);
            }
          }
        }
        else if (endTagName == psiElement) {
          if (!(tag instanceof HtmlTag) && !tag.getName().equals(endTagName.getText())) {
            registerProblemEnd(holder, tag, endTagName);
          }
        }
      }
    }
    else if (psiElement instanceof PsiErrorElement) {
      PsiElement[] children = psiElement.getChildren();
      for (PsiElement token : children) {
        if (token instanceof XmlToken && XmlTokenType.XML_NAME == ((XmlToken)token).getTokenType()) {
          PsiFile psiFile = holder.getCurrentAnnotationSession().getFile();

          if (HTMLLanguage.INSTANCE == psiFile.getViewProvider().getBaseLanguage() || HTMLLanguage.INSTANCE == psiElement.getLanguage()) {
            String message = XmlPsiBundle.message("xml.parsing.closing.tag.matches.nothing");

            if (message.equals(((PsiErrorElement)psiElement).getErrorDescription()) && psiFile.getContext() == null) {
              holder.newAnnotation(HighlightSeverity.WARNING, message).range(psiElement).withFix(new RemoveExtraClosingTagIntentionAction()).create();
            }
          }
        }
      }
    }
  }

  private static void registerProblemStart(@NotNull AnnotationHolder holder,
                                           @NotNull XmlTag tag,
                                           @NotNull XmlToken start,
                                           @NotNull XmlToken end) {
    PsiElement context = tag.getContainingFile().getContext();
    if (context != null) {
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(context.getLanguage());
      if (parserDefinition != null) {
        ASTNode contextNode = context.getNode();
        if (contextNode != null) {
          // TODO: we should check for concatenations here
          return;
        }
      }
    }
    String tagName = tag.getName();
    String endTokenText = end.getText();

    RenameTagBeginOrEndIntentionAction renameEndAction = new RenameTagBeginOrEndIntentionAction(tagName, endTokenText, false);
    RenameTagBeginOrEndIntentionAction renameStartAction = new RenameTagBeginOrEndIntentionAction(endTokenText, tagName, true);

    holder.newAnnotation(HighlightSeverity.ERROR, XmlAnalysisBundle.message("xml.inspections.tag.has.wrong.closing.tag.name"))
      .range(start)
      .withFix(renameEndAction)
      .withFix(renameStartAction)
      .create();
  }

  private static void registerProblemEnd(@NotNull AnnotationHolder holder,
                                         @NotNull XmlTag tag,
                                         @NotNull XmlToken end) {
    PsiElement context = tag.getContainingFile().getContext();
    if (context != null) {
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(context.getLanguage());
      if (parserDefinition != null) {
        ASTNode contextNode = context.getNode();
        if (contextNode != null) {
          // TODO: we should check for concatenations here
          return;
        }
      }
    }
    String tagName = tag.getName();
    String endTokenText = end.getText();

    RenameTagBeginOrEndIntentionAction renameEndAction = new RenameTagBeginOrEndIntentionAction(tagName, endTokenText, false);
    RenameTagBeginOrEndIntentionAction renameStartAction = new RenameTagBeginOrEndIntentionAction(endTokenText, tagName, true);

    holder.newAnnotation(HighlightSeverity.ERROR, XmlAnalysisBundle.message("xml.inspections.wrong.closing.tag.name"))
      .range(end)
      .withFix(new RemoveExtraClosingTagIntentionAction())
      .withFix(renameEndAction)
      .withFix(renameStartAction)
      .create();
  }

  @Nullable
  static XmlToken findEndTagName(@Nullable PsiErrorElement element) {
    if (element == null) return null;

    ASTNode astNode = element.getNode();
    if (astNode == null) return null;

    ASTNode current = astNode.getLastChildNode();
    ASTNode prev = current;

    while (current != null) {
      IElementType elementType = prev.getElementType();

      if ((elementType == XmlTokenType.XML_NAME || elementType == XmlTokenType.XML_TAG_NAME) &&
          current.getElementType() == XmlTokenType.XML_END_TAG_START) {
        return (XmlToken)prev.getPsi();
      }

      prev = current;
      current = current.getTreePrev();
    }

    return null;
  }
}
