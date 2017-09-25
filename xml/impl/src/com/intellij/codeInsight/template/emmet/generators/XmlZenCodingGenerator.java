/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.template.emmet.generators;

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.editorActions.XmlEditUtil;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.ZenCodingTemplate;
import com.intellij.codeInsight.template.emmet.ZenCodingUtil;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.lang.html.HtmlQuotesFormatPreprocessor;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.util.DocumentUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class XmlZenCodingGenerator extends ZenCodingGenerator {
  @Override
  public TemplateImpl generateTemplate(@NotNull TemplateToken token, boolean hasChildren, @NotNull PsiElement context) {
    String s = toString(token, hasChildren, context);
    TemplateImpl tokenTemplate = token.getTemplate();
    assert tokenTemplate != null;
    TemplateImpl template = tokenTemplate.copy();
    template.setString(s);
    return template;
  }

  @Override
  public TemplateImpl createTemplateByKey(@NotNull String key, boolean forceSingleTag) {
    StringBuilder builder = new StringBuilder("<");
    builder.append(key).append('>');
    if (!forceSingleTag && !HtmlUtil.isSingleHtmlTag(key)) {
      builder.append("$END$</").append(key).append('>');
    }
    return new TemplateImpl("", builder.toString(), "");
  }

  @NotNull
  private String toString(@NotNull TemplateToken token, boolean hasChildren, @NotNull PsiElement context) {
    CodeStyleSettings.QuoteStyle quoteStyle = XmlEditUtil.quoteStyle(context.getContainingFile());
    XmlTag tag = token.getXmlTag();
    if (tag != null) {
      if (quoteStyle != CodeStyleSettings.QuoteStyle.None) {
        DocumentUtil.writeInRunUndoTransparentAction(() -> HtmlQuotesFormatPreprocessor.HtmlQuotesConverter.runOnElement(quoteStyle, tag));
      }
      return replaceQuotesIfNeeded(toString(tag, token.getAttributes(), hasChildren, context), context.getContainingFile());
    }

    PsiFile file = token.getFile();
    if (quoteStyle != CodeStyleSettings.QuoteStyle.None) {
      DocumentUtil.writeInRunUndoTransparentAction(() -> HtmlQuotesFormatPreprocessor.HtmlQuotesConverter.runOnElement(quoteStyle, file));
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

  @NotNull
  public abstract String buildAttributesString(@NotNull Map<String, String> attribute2value,
                                               boolean hasChildren,
                                               int numberInIteration,
                                               int totalIterations, @Nullable String surroundedText);

  @Override
  public abstract boolean isMyContext(@NotNull CustomTemplateCallback callback, boolean wrapping);

  @Nullable
  @Override
  public String computeTemplateKey(@NotNull CustomTemplateCallback callback) {
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
               AttachmentFactory.createAttachment(editor.getDocument()));
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
