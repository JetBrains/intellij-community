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
package com.intellij.codeInsight.template.zencoding;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.TemplateInvokationListener;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.HashSet;
import org.apache.xerces.util.XML11Char;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlZenCodingTemplate implements CustomLiveTemplate {
  static final char MARKER = '$';
  private static final String OPERATIONS = ">+*";
  private static final String SELECTORS = ".#[";
  private static final String ID = "id";
  private static final String CLASS = "class";

  private static int parseNonNegativeInt(@NotNull String s) {
    try {
      return Integer.parseInt(s);
    }
    catch (Throwable ignored) {
    }
    return -1;
  }

  private static String getPrefix(@NotNull String templateKey) {
    for (int i = 0, n = templateKey.length(); i < n; i++) {
      char c = templateKey.charAt(i);
      if (SELECTORS.indexOf(c) >= 0) {
        return templateKey.substring(0, i);
      }
    }
    return templateKey;
  }

  @Nullable
  private static Pair<String, String> parseAttrNameAndValue(@NotNull String text) {
    int eqIndex = text.indexOf('=');
    if (eqIndex > 0) {
      return new Pair<String, String>(text.substring(0, eqIndex), text.substring(eqIndex + 1));
    }
    return null;
  }

  @Nullable
  private static TemplateToken parseSelectors(@NotNull String text) {
    String templateKey = null;
    List<Pair<String, String>> attributes = new ArrayList<Pair<String, String>>();
    Set<String> definedAttrs = new HashSet<String>();
    final List<String> classes = new ArrayList<String>();
    StringBuilder builder = new StringBuilder();
    char lastDelim = 0;
    text += MARKER;
    int classAttrPosition = -1;
    for (int i = 0, n = text.length(); i < n; i++) {
      char c = text.charAt(i);
      if (c == '#' || c == '.' || c == '[' || c == ']' || i == n - 1) {
        if (c != ']') {
          switch (lastDelim) {
            case 0:
              templateKey = builder.toString();
              break;
            case '#':
              if (!definedAttrs.add(ID)) {
                return null;
              }
              attributes.add(new Pair<String, String>(ID, builder.toString()));
              break;
            case '.':
              if (builder.length() <= 0) {
                return null;
              }
              if (classAttrPosition < 0) {
                classAttrPosition = attributes.size();
              }
              classes.add(builder.toString());
              break;
            case ']':
              if (builder.length() > 0) {
                return null;
              }
              break;
            default:
              return null;
          }
        }
        else if (lastDelim != '[') {
          return null;
        }
        else {
          Pair<String, String> pair = parseAttrNameAndValue(builder.toString());
          if (pair == null || !definedAttrs.add(pair.first)) {
            return null;
          }
          attributes.add(pair);
        }
        lastDelim = c;
        builder = new StringBuilder();
      }
      else {
        builder.append(c);
      }
    }
    if (classes.size() > 0) {
      if (definedAttrs.contains(CLASS)) {
        return null;
      }
      StringBuilder classesAttrValue = new StringBuilder();
      for (int i = 0; i < classes.size(); i++) {
        classesAttrValue.append(classes.get(i));
        if (i < classes.size() - 1) {
          classesAttrValue.append(' ');
        }
      }
      assert classAttrPosition >= 0;
      attributes.add(classAttrPosition, new Pair<String, String>(CLASS, classesAttrValue.toString()));
    }
    return new TemplateToken(templateKey, attributes);
  }

  private static boolean isXML11ValidQName(String str) {
    final int colon = str.indexOf(':');
    if (colon == 0 || colon == str.length() - 1) {
      return false;
    }
    if (colon > 0) {
      final String prefix = str.substring(0, colon);
      final String localPart = str.substring(colon + 1);
      return XML11Char.isXML11ValidNCName(prefix) && XML11Char.isXML11ValidNCName(localPart);
    }
    return XML11Char.isXML11ValidNCName(str);
  }

  private static boolean generateTemplateAndAddToToken(TemplateToken token, CustomTemplateCallback callback) {
    TemplateImpl template = callback.findApplicableTemplate(token.myKey);
    assert template != null;
    XmlTag tag = parseXmlTagInTemplate(template.getString(), callback.getProject());
    if (tag == null) {
      return false;
    }
    token.myTemplate = template;
    return true;
  }

  @Nullable
  private static List<Token> parse(@NotNull String text, @NotNull CustomTemplateCallback callback) {
    text += MARKER;
    StringBuilder templateKeyBuilder = new StringBuilder();
    List<Token> result = new ArrayList<Token>();
    for (int i = 0, n = text.length(); i < n; i++) {
      char c = text.charAt(i);
      if (i == n - 1 || (i < n - 2 && OPERATIONS.indexOf(c) >= 0)) {
        String key = templateKeyBuilder.toString();
        templateKeyBuilder = new StringBuilder();
        int num = parseNonNegativeInt(key);
        if (num > 0) {
          result.add(new NumberToken(num));
        }
        else {
          if (key.length() == 0) {
            return null;
          }
          String prefix = getPrefix(key);
          boolean applicable = callback.isLiveTemplateApplicable(prefix);
          if (!applicable && !isXML11ValidQName(prefix)) {
            return null;
          }
          TemplateToken token = parseSelectors(key);
          if (token == null) {
            return null;
          }
          if (applicable && token.myAttribute2Value.size() > 0) {
            assert prefix.equals(token.myKey);
            if (!generateTemplateAndAddToToken(token, callback)) {
              return null;
            }
          }
          result.add(token);
        }
        result.add(i < n - 1 ? new OperationToken(c) : new MarkerToken());
      }
      else if (!Character.isWhitespace(c)) {
        templateKeyBuilder.append(c);
      }
      else {
        return null;
      }
    }
    return result;
  }

  private static boolean check(@NotNull Collection<Token> tokens) {
    State state = State.WORD;
    for (Token token : tokens) {
      if (token instanceof MarkerToken) {
        break;
      }
      switch (state) {
        case OPERATION:
          if (token instanceof OperationToken) {
            state = ((OperationToken)token).mySign == '*' ? State.NUMBER : State.WORD;
          }
          else {
            return false;
          }
          break;
        case WORD:
          if (token instanceof TemplateToken) {
            state = State.OPERATION;
          }
          else {
            return false;
          }
          break;
        case NUMBER:
          if (token instanceof NumberToken) {
            state = State.AFTER_NUMBER;
          }
          else {
            return false;
          }
          break;
        case AFTER_NUMBER:
          if (token instanceof OperationToken && ((OperationToken)token).mySign != '*') {
            state = State.WORD;
          }
          else {
            return false;
          }
          break;
      }
    }
    return state == State.OPERATION || state == State.AFTER_NUMBER;
  }

  private static String computeKey(Editor editor, int startOffset) {
    int offset = editor.getCaretModel().getOffset();
    String s = editor.getDocument().getCharsSequence().subSequence(startOffset, offset).toString();
    int index = 0;
    while (index < s.length() && Character.isWhitespace(s.charAt(index))) {
      index++;
    }
    String key = s.substring(index);
    int lastWhitespaceIndex = -1;
    for (int i = 0; i < key.length(); i++) {
      if (Character.isWhitespace(key.charAt(i))) {
        lastWhitespaceIndex = i;
      }
    }
    if (lastWhitespaceIndex >= 0 && lastWhitespaceIndex < key.length() - 1) {
      return key.substring(lastWhitespaceIndex + 1);
    }
    return key;
  }

  public String computeTemplateKey(@NotNull CustomTemplateCallback callback) {
    Editor editor = callback.getEditor();
    int offset = callback.getOffset();
    PsiElement element = callback.getFile().findElementAt(offset > 0 ? offset - 1 : offset);
    int line = editor.getCaretModel().getLogicalPosition().line;
    int lineStart = editor.getDocument().getLineStartOffset(line);
    int parentStart;
    do {
      parentStart = element != null ? element.getTextRange().getStartOffset() : 0;
      int startOffset = parentStart > lineStart ? parentStart : lineStart;
      String key = computeKey(editor, startOffset);
      List<Token> tokens = parse(key, callback);
      if (tokens != null && check(tokens)) {
        if (tokens.size() == 2) {
          Token token = tokens.get(0);
          if (token instanceof TemplateToken) {
            if (key.equals(((TemplateToken)token).myKey) && callback.isLiveTemplateApplicable(key)) {
              // do not activate only live template
              return null;
            }
          }
        }
        return key;
      }
      if (element != null) {
        element = element.getParent();
      }
    }
    while (element != null && parentStart > lineStart);
    return null;
  }

  public boolean isApplicable(PsiFile file, int offset, boolean selection) {
    WebEditorOptions webEditorOptions = WebEditorOptions.getInstance();
    if (!webEditorOptions.isZenCodingEnabled()) {
      return false;
    }
    if (file.getLanguage() instanceof XMLLanguage) {
      PsiElement element = file.findElementAt(offset > 0 ? offset - 1 : offset);
      if (element == null || element.getLanguage() instanceof XMLLanguage) {
        return true;
      }
    }
    return false;
  }

  public void expand(String key, @NotNull CustomTemplateCallback callback, @Nullable TemplateInvokationListener listener) {
    List<Token> tokens = parse(key, callback);
    assert tokens != null;
    XmlZenCodingInterpreter interpreter = new XmlZenCodingInterpreter(tokens, callback, State.WORD, listener);
    interpreter.invoke(0);
  }

  public void wrap(String selection, @NotNull CustomTemplateCallback callback, @Nullable TemplateInvokationListener listener) {
  }

  @Nullable
  static XmlTag parseXmlTagInTemplate(String templateString, Project project) {
    XmlFile xmlFile = (XmlFile)PsiFileFactory.getInstance(project).createFileFromText("dummy.xml", templateString);
    XmlDocument document = xmlFile.getDocument();
    return document == null ? null : document.getRootTag();
  }
}