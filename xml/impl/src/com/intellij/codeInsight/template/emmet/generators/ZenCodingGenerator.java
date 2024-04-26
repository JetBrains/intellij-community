// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.generators;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.EmmetParser;
import com.intellij.codeInsight.template.emmet.XmlEmmetParser;
import com.intellij.codeInsight.template.emmet.ZenCodingTemplate;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.codeInsight.template.emmet.tokens.ZenCodingToken;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public abstract class ZenCodingGenerator {
  private static final ExtensionPointName<ZenCodingGenerator> EP_NAME = new ExtensionPointName<>("com.intellij.xml.zenCodingGenerator");

  public abstract TemplateImpl generateTemplate(@NotNull TemplateToken token, boolean hasChildren, @NotNull PsiElement context);

  public @Nullable TemplateImpl createTemplateByKey(@NotNull String key, boolean forceSingleTag) {
    return null;
  }

  public abstract boolean isMyContext(@NotNull CustomTemplateCallback callback, boolean wrapping);

  public @Nullable String getSuffix() {
    return null;
  }

  public abstract boolean isAppliedByDefault(@NotNull PsiElement context);

  public abstract boolean isEnabled();

  public static @NotNull List<ZenCodingGenerator> getInstances() {
    return EP_NAME.getExtensionList();
  }

  public @Nullable String computeTemplateKey(@NotNull CustomTemplateCallback callback) {
    Editor editor = callback.getEditor();
    int currentOffset = editor.getCaretModel().getOffset();
    int startOffset = editor.getDocument().getLineStartOffset(editor.getCaretModel().getLogicalPosition().line);
    String key = computeKey(editor.getDocument().getCharsSequence().subSequence(startOffset, currentOffset));
    return !StringUtil.isEmpty(key) && ZenCodingTemplate.checkTemplateKey(key, callback, this) ? key : null;
  }

  protected @Nullable String computeKey(@NotNull CharSequence text) {
    int currentOffset = text.length();
    int groupCount = 0;
    int bracketCount = 0;
    int textCount = 0;

    while (currentOffset > 0) {
      currentOffset--;
      char c = text.charAt(currentOffset);

      if (c == ']') {
        bracketCount++;
      }
      else if (c == '[') {
        if (bracketCount == 0) {
          currentOffset++;
          break;
        }
        bracketCount--;
      }
      else if (c == '}') {
        textCount++;
      }
      else if (c == '{') {
        if (textCount == 0) {
          currentOffset++;
          break;
        }
        textCount--;
      }
      else if (c == ')') {
        groupCount++;
      }
      else if (c == '(') {
        if (groupCount == 0) {
          currentOffset++;
          break;
        }
        groupCount--;
      }
      else {
        if (bracketCount > 0 || textCount > 0) {
          // respect all characters inside attribute sets or text nodes
          continue;
        }
        if (!isAllowedChar(c)) {
          currentOffset++;
          break;
        }
      }
    }
    return groupCount == 0 && textCount == 0 && bracketCount == 0 && currentOffset < text.length()
           ? text.subSequence(currentOffset, text.length()).toString().replaceFirst("^[*+>^]+", "")
           : null;
  }

  protected boolean isAllowedChar(char c) {
    return Character.isDigit(c) || Character.isLetter(c) || StringUtil.containsChar("/>+^[](){}#.*:$-_!@|%", c);
  }

  public @NotNull EmmetParser createParser(List<ZenCodingToken> tokens,
                                           CustomTemplateCallback callback,
                                           ZenCodingGenerator generator,
                                           boolean surroundWithTemplate) {
    return new XmlEmmetParser(tokens, callback, generator, surroundWithTemplate);
  }

  public @Nullable Configurable createConfigurable() {
    return null;
  }
  
  public boolean addToCompletion() {
    return hasCompletionItem();
  }

  public boolean hasCompletionItem() {
    return false;
  }

  public boolean isHtml(@NotNull CustomTemplateCallback callback) {
    return false;
  }

  public void disableEmmet() {
  }
}
