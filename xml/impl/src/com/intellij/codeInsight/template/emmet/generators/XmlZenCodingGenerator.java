// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.generators;

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.editorActions.XmlEditUtil;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.ZenCodingTemplate;
import com.intellij.codeInsight.template.emmet.ZenCodingUtil;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.diagnostic.CoreAttachmentFactory;
import com.intellij.lang.html.HtmlQuotesConverter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class XmlZenCodingGenerator extends ZenCodingGenerator {
  @Override
  public TemplateImpl generateTemplate(@NotNull TemplateToken token, boolean hasChildren, @NotNull PsiElement context) {
    TemplateImpl tokenTemplate = token.getTemplate();
    String s = toString(token, hasChildren, context);
    assert tokenTemplate != null;
    TemplateImpl template = tokenTemplate.copy();
    template.setString(s);
    return template;
  }

  @Override
  public TemplateImpl createTemplateByKey(@NotNull String key, boolean forceSingleTag) {
    StringBuilder builder = new StringBuilder("<");
    builder.append(key).append('>');
    if (!forceSingleTag && !HtmlUtil.isSingleHtmlTag(key, false)) {
      builder.append("$END$</").append(key).append('>');
    }
    return new TemplateImpl("", builder.toString(), "");
  }

  private @NotNull String toString(@NotNull TemplateToken token, boolean hasChildren, @NotNull PsiElement context) {
    CodeStyleSettings.QuoteStyle quoteStyle = XmlEditUtil.quoteStyle(context.getContainingFile());
    XmlTag tag = token.getXmlTag();
    if (tag != null) {
      if (quoteStyle != CodeStyleSettings.QuoteStyle.None) {
        HtmlQuotesConverter.runOnElement(quoteStyle, tag);
        //hack: formatter change the document, so we have to apply changes from document back to PSI, since events are disables for the file
        Document document = token.getFile().getViewProvider().getDocument();
        token.setTemplateText(document.getText(), token.getFile());
      }

      return replaceQuotesIfNeeded(toString(token.getXmlTag(), token.getAttributes(), hasChildren, context),
                                   context.getContainingFile());
    }

    PsiFile file = token.getFile();
    if (quoteStyle != CodeStyleSettings.QuoteStyle.None) {
      HtmlQuotesConverter.runOnElement(quoteStyle, file);
    }
    return replaceQuotesIfNeeded(file.getText(), context.getContainingFile());
  }

  private static String replaceQuotesIfNeeded(@NotNull String text, @NotNull PsiFile file) {
    PsiElement context = file.getContext();
    if (context != null) {
      String contextText = context.getText();
      if (StringUtil.startsWithChar(contextText, '"')) {
        return StringUtil.escapeChar(text, '"');
      }
      else if (StringUtil.startsWithChar(contextText, '\'')) {
        return StringUtil.escapeChar(text, '\'');
      }
    }
    return text;
  }

  public abstract String toString(@NotNull XmlTag tag,
                                  @NotNull Map<String, String> attributes,
                                  boolean hasChildren,
                                  @NotNull PsiElement context);

  public abstract @NotNull String buildAttributesString(@NotNull Map<String, String> attribute2value,
                                                        boolean hasChildren,
                                                        int numberInIteration,
                                                        int totalIterations, @Nullable String surroundedText);

  @Override
  public abstract boolean isMyContext(@NotNull CustomTemplateCallback callback, boolean wrapping);

  @Override
  public @Nullable String computeTemplateKey(@NotNull CustomTemplateCallback callback) {
    Editor editor = callback.getEditor();
    int currentOffset = editor.getCaretModel().getOffset();
    int startOffset = Math.min(editor.getDocument().getLineStartOffset(editor.getDocument().getLineNumber(currentOffset)), currentOffset);
    CharSequence documentText = editor.getDocument().getCharsSequence();
    PsiElement prevVisibleLeaf = callback.getContext();
    while (prevVisibleLeaf != null) {
      TextRange textRange = prevVisibleLeaf.getTextRange();
      final int endOffset = textRange.getEndOffset();
      if (endOffset <= currentOffset) {
        if (endOffset <= startOffset) {
          break;
        }
        IElementType prevType = prevVisibleLeaf.getNode().getElementType();
        if (prevType == XmlTokenType.XML_TAG_END || prevType == XmlTokenType.XML_EMPTY_ELEMENT_END) {
          startOffset = endOffset;
          break;
        }
      }
      prevVisibleLeaf = PsiTreeUtil.prevVisibleLeaf(prevVisibleLeaf);
    }

    if (startOffset < 0 || currentOffset > documentText.length() || currentOffset < startOffset) {
      Logger.getInstance(getClass())
        .error("Error while calculating emmet abbreviation. Offset: " + currentOffset + "; Start: " + startOffset,
               CoreAttachmentFactory.createAttachment(editor.getDocument()));
      return null;
    }
    String key = computeKey(documentText.subSequence(startOffset, currentOffset));
    return !StringUtil.isEmpty(key) && ZenCodingTemplate.checkTemplateKey(key, callback, this) ? key : null;
  }

  @Override
  public void disableEmmet() {
    EmmetOptions.getInstance().setEmmetEnabled(false);
  }

  @Override
  public boolean isHtml(@NotNull CustomTemplateCallback callback) {
    return ZenCodingUtil.isHtml(callback);
  }
}
