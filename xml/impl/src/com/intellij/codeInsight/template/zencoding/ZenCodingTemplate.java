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
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.TemplateInvokationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class ZenCodingTemplate implements CustomLiveTemplate {
  static final char MARKER = '$';
  private static final String OPERATIONS = ">+*";

  private static int parseNonNegativeInt(@NotNull String s) {
    try {
      return Integer.parseInt(s);
    }
    catch (Throwable ignored) {
    }
    return -1;
  }

  @Nullable
  private List<Token> parse(@NotNull String text, @NotNull CustomTemplateCallback callback) {
    String filter = null;

    int filterDelim = text.indexOf('|');
    if (filterDelim >= 0 && filterDelim < text.length() - 1) {
      filter = text.substring(filterDelim + 1);
      text = text.substring(0, filterDelim);
    }

    boolean inQuotes = false;
    boolean inApostrophes = false;
    text += MARKER;
    StringBuilder templateKeyBuilder = new StringBuilder();
    List<Token> result = new ArrayList<Token>();
    for (int i = 0, n = text.length(); i < n; i++) {
      char c = text.charAt(i);
      if (inQuotes || inApostrophes) {
        templateKeyBuilder.append(c);
        if (c == '"') inQuotes = false;
        else if (c == '\'') inApostrophes = false;
      }
      else if (i == n - 1 || (i < n - 2 && OPERATIONS.indexOf(c) >= 0)) {
        String key = templateKeyBuilder.toString();
        templateKeyBuilder = new StringBuilder();
        int num = parseNonNegativeInt(key);
        if (num > 0) {
          result.add(new NumberToken(num));
        }
        else {
          TemplateToken token = parseTemplateKey(key, callback);
          if (token == null) return null;
          result.add(token);
        }
        result.add(i < n - 1 ? new OperationToken(c) : new MarkerToken());
      }
      else if (!Character.isWhitespace(c)) {
        templateKeyBuilder.append(c);
        if (c == '"') inQuotes = true;
        else if (c == '\'') inApostrophes = true;
      }
      else {
        return null;
      }
    }

    if (inQuotes || inApostrophes) {
      return null;
    }

    if (filter != null) {
      result.add(new FilterToken(filter));
    }
    return result;
  }

  @Nullable
  protected abstract TemplateToken parseTemplateKey(String key, CustomTemplateCallback callback);

  private static boolean check(@NotNull Collection<Token> tokens) {
    State state = State.WORD;
    for (Token token : tokens) {
      if (token instanceof MarkerToken) {
        break;
      }
      switch (state) {
        case OPERATION:
          if (token instanceof OperationToken) {
            state = ((OperationToken)token).getSign() == '*' ? State.NUMBER : State.WORD;
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
          if (token instanceof OperationToken && ((OperationToken)token).getSign() != '*') {
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
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      if (lastQuoteIndex >= 0 || lastApostropheIndex >= 0) {
        if (c == '"') lastQuoteIndex = -1;
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

  protected boolean checkTemplateKey(String key, CustomTemplateCallback callback) {
    List<Token> tokens = parse(key, callback);
    if (tokens != null && check(tokens)) {
      return true;
    }
    return false;
  }

  public void expand(String key, @NotNull CustomTemplateCallback callback) {
    expand(key, callback, null);
  }

  private void expand(String key,
                      @NotNull CustomTemplateCallback callback,
                      String surroundedText) {
    List<Token> tokens = parse(key, callback);
    assert tokens != null;
    if (surroundedText == null) {
      if (tokens.size() == 2) {
        Token token = tokens.get(0);
        if (token instanceof TemplateToken) {
          if (key.equals(((TemplateToken)token).getKey()) && callback.findApplicableTemplates(key).size() > 1) {
            callback.startTemplate();
            return;
          }
        }
      }
      callback.deleteTemplateKey(key);
    }
    XmlZenCodingInterpreter.interpret(tokens, 0, callback, State.WORD, surroundedText);
  }

  public void wrap(final String selection,
                   @NotNull final CustomTemplateCallback callback,
                   @Nullable final TemplateInvokationListener listener) {
    InputValidatorEx validator = new InputValidatorEx() {
      public String getErrorText(String inputString) {
        if (!checkTemplateKey(inputString, callback)) {
          return XmlBundle.message("zen.coding.incorrect.abbreviation.error");
        }
        return null;
      }

      public boolean checkInput(String inputString) {
        return getErrorText(inputString) == null;
      }

      public boolean canClose(String inputString) {
        return checkInput(inputString);
      }
    };
    final String abbreviation = Messages
      .showInputDialog(callback.getProject(), XmlBundle.message("zen.coding.enter.abbreviation.dialog.label"),
                       XmlBundle.message("zen.coding.title"), Messages.getQuestionIcon(), "", validator);
    if (abbreviation != null) {
      doWrap(selection, abbreviation, callback, listener);
    }
  }

  public boolean isApplicable(PsiFile file, int offset) {
    WebEditorOptions webEditorOptions = WebEditorOptions.getInstance();
    if (!webEditorOptions.isZenCodingEnabled()) {
      return false;
    }
    if (file == null) {
      return false;
    }
    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();
    PsiElement element = CustomTemplateCallback.getContext(file, offset);
    return isApplicable(element);
  }

  protected abstract boolean isApplicable(@NotNull PsiElement element);

  protected void doWrap(final String selection,
                        final String abbreviation,
                        final CustomTemplateCallback callback,
                        final TemplateInvokationListener listener) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(callback.getProject(), new Runnable() {
          public void run() {
            EditorModificationUtil.deleteSelectedText(callback.getEditor());
            PsiDocumentManager.getInstance(callback.getProject()).commitAllDocuments();
            callback.fixInitialState();
            expand(abbreviation, callback, selection);
            if (listener != null) {
              listener.finished();
            }
          }
        }, CodeInsightBundle.message("insert.code.template.command"), null);
      }
    });
  }

  @NotNull
  public String getTitle() {
    return XmlBundle.message("zen.coding.title");
  }

  public char getShortcut() {
    return (char)WebEditorOptions.getInstance().getZenCodingExpandShortcut();
  }
}
