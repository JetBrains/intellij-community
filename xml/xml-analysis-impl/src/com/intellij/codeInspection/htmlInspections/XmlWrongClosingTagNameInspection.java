// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
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
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class XmlWrongClosingTagNameInspection implements Annotator {

  @Override
  public void annotate(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder) {
    if (psiElement instanceof XmlToken) {
      final PsiElement parent = psiElement.getParent();
      if (parent instanceof XmlTag) {
        final XmlTag tag = (XmlTag)parent;
        final XmlToken start = XmlTagUtil.getStartTagNameElement(tag);
        XmlToken endTagName = XmlTagUtil.getEndTagNameElement(tag);
        if (start == psiElement) {
          if (endTagName != null && !(tag instanceof HtmlTag) && !tag.getName().equals(endTagName.getText())) {
            registerProblemStart(holder, tag, start, endTagName);
          }
          else if (endTagName == null && !(tag instanceof HtmlTag && HtmlUtil.isSingleHtmlTag(tag, true))) {
            final PsiErrorElement errorElement = PsiTreeUtil.getChildOfType(tag, PsiErrorElement.class);
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
      else if (parent instanceof PsiErrorElement) {
        if (XmlTokenType.XML_NAME == ((XmlToken)psiElement).getTokenType()) {
          final PsiFile psiFile = psiElement.getContainingFile();

          if (psiFile != null && (HTMLLanguage.INSTANCE == psiFile.getViewProvider().getBaseLanguage() || HTMLLanguage.INSTANCE == parent.getLanguage())) {
            final String message = XmlErrorMessages.message("xml.parsing.closing.tag.matches.nothing");

            if (message.equals(((PsiErrorElement)parent).getErrorDescription()) &&
                psiFile.getContext() == null
               ) {
              final Annotation annotation = holder.createWarningAnnotation(parent, message);
              annotation.registerFix(new RemoveExtraClosingTagIntentionAction());
            }
          }
        }
      }
    }
  }

  private static void registerProblemStart(@NotNull final AnnotationHolder holder,
                                      @NotNull final XmlTag tag,
                                      @NotNull final XmlToken start,
                                      @NotNull final XmlToken end) {
    PsiElement context = tag.getContainingFile().getContext();
    if (context != null) {
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(context.getLanguage());
      if (parserDefinition != null) {
        ASTNode contextNode = context.getNode();
        if (contextNode != null && contextNode.getChildren(parserDefinition.getStringLiteralElements()) != null) {
          // TODO: we should check for concatenations here
          return;
        }
      }
    }
    final String tagName = tag.getName();
    final String endTokenText = end.getText();

    final RenameTagBeginOrEndIntentionAction renameEndAction = new RenameTagBeginOrEndIntentionAction(tagName, endTokenText, false);
    final RenameTagBeginOrEndIntentionAction renameStartAction = new RenameTagBeginOrEndIntentionAction(endTokenText, tagName, true);

    final Annotation annotation = holder.createErrorAnnotation(start, XmlErrorMessages.message("tag.has.wrong.closing.tag.name"));
    annotation.registerFix(renameEndAction);
    annotation.registerFix(renameStartAction);
  }

  private static void registerProblemEnd(@NotNull final AnnotationHolder holder,
                                         @NotNull final XmlTag tag,
                                         @NotNull final XmlToken end) {
    PsiElement context = tag.getContainingFile().getContext();
    if (context != null) {
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(context.getLanguage());
      if (parserDefinition != null) {
        ASTNode contextNode = context.getNode();
        if (contextNode != null && contextNode.getChildren(parserDefinition.getStringLiteralElements()) != null) {
          // TODO: we should check for concatenations here
          return;
        }
      }
    }
    final String tagName = tag.getName();
    final String endTokenText = end.getText();

    final RenameTagBeginOrEndIntentionAction renameEndAction = new RenameTagBeginOrEndIntentionAction(tagName, endTokenText, false);
    final RenameTagBeginOrEndIntentionAction renameStartAction = new RenameTagBeginOrEndIntentionAction(endTokenText, tagName, true);

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
