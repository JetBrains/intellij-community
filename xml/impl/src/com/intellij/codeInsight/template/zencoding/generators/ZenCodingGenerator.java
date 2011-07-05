/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.zencoding.generators;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.zencoding.ZenCodingTemplate;
import com.intellij.codeInsight.template.zencoding.tokens.TemplateToken;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * @author Eugene.Kudelevsky
 */
public abstract class ZenCodingGenerator {
  private static final ExtensionPointName<ZenCodingGenerator> EP_NAME =
    new ExtensionPointName<ZenCodingGenerator>("com.intellij.xml.zenCodingGenerator");

  public abstract TemplateImpl generateTemplate(@NotNull TemplateToken token, boolean hasChildren, @NotNull PsiElement context);

  @Nullable
  public TemplateImpl createTemplateByKey(@NotNull String key) {
    return null;
  }

  public abstract boolean isMyContext(@NotNull PsiElement context, boolean wrapping);

  @Nullable
  public String getSuffix() {
    return null;
  }

  public abstract boolean isAppliedByDefault(@NotNull PsiElement context);

  public static List<ZenCodingGenerator> getInstances() {
    List<ZenCodingGenerator> generators = new ArrayList<ZenCodingGenerator>();
    Collections.addAll(generators, XmlZenCodingGeneratorImpl.INSTANCE);
    Collections.addAll(generators, EP_NAME.getExtensions());
    return generators;
  }

  @Nullable
  public String computeTemplateKey(@NotNull CustomTemplateCallback callback) {
    Editor editor = callback.getEditor();
    PsiElement element = callback.getContext();
    int line = editor.getCaretModel().getLogicalPosition().line;
    int lineStart = editor.getDocument().getLineStartOffset(line);
    int elementStart;
    do {
      elementStart = element.getTextRange().getStartOffset();
      int startOffset = elementStart > lineStart ? elementStart : lineStart;
      String key = computeKey(editor, startOffset);
      if (key != null) {
        while (key.length() > 0 && !ZenCodingTemplate.checkTemplateKey(key, callback, this)) {
          key = key.substring(1);
        }
        if (key.length() > 0) {
          return key;
        }
      }
      element = element.getParent();
    }
    while (element != null && elementStart > lineStart);
    return null;
  }

  @Nullable
  protected static String computeKey(Editor editor, int startOffset) {
    int offset = editor.getCaretModel().getOffset();
    String s = editor.getDocument().getCharsSequence().subSequence(startOffset, offset).toString();
    int index = 0;
    while (index < s.length() && Character.isWhitespace(s.charAt(index))) {
      index++;
    }
    String key = s.substring(index);
    int lastWhitespaceIndex = -1;
    int lastQuoteIndex = -1;
    int lastApostropheIndex = -1;
    boolean inBrackets = false;
    int bracesStack = 0;

    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      if (lastQuoteIndex >= 0 || lastApostropheIndex >= 0) {
        if (c == '"') {
          lastQuoteIndex = -1;
        }
        else if (c == '\'') lastApostropheIndex = -1;
      }
      else if (Character.isWhitespace(c)) {
        lastWhitespaceIndex = i;
      }
      else if (c == '"') {
        lastQuoteIndex = i;
      }
      else if (c == '\'') {
        lastApostropheIndex = i;
      }
      else if (c == '[') {
        inBrackets = true;
      }
      else if (c == ']' && inBrackets) {
        lastWhitespaceIndex = -1;
        inBrackets = false;
      }
      else if (c == '{') {
        bracesStack++;
      }
      else if (c == '}' && bracesStack > 0) {
        bracesStack--;
        if (bracesStack == 0) {
          lastWhitespaceIndex = -1;
        }
      }
    }
    if (lastQuoteIndex >= 0 || lastApostropheIndex >= 0) {
      int max = Math.max(lastQuoteIndex, lastApostropheIndex);
      return max < key.length() - 1 ? key.substring(max) : null;
    }
    if (lastWhitespaceIndex >= 0 && lastWhitespaceIndex < key.length() - 1) {
      return key.substring(lastWhitespaceIndex + 1);
    }
    return key;
  }
}
