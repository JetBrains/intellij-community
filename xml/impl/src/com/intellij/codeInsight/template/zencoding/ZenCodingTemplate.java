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
import com.intellij.codeInsight.template.LiveTemplateBuilder;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.zencoding.filters.ZenCodingGenerator;
import com.intellij.codeInsight.template.zencoding.nodes.*;
import com.intellij.codeInsight.template.zencoding.tokens.*;
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
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class ZenCodingTemplate implements CustomLiveTemplate {
  public static final char MARKER = '$';
  private static final String DELIMS = ">+*()";
  public static final String ATTRS = "ATTRS";

  private static int parseNonNegativeInt(@NotNull String s) {
    try {
      return Integer.parseInt(s);
    }
    catch (Throwable ignored) {
    }
    return -1;
  }

  @Nullable
  private ZenCodingNode parse(@NotNull String text, @NotNull CustomTemplateCallback callback) {
    List<ZenCodingToken> tokens = lex(text, callback);
    if (tokens == null) {
      return null;
    }
    MyParser parser = new MyParser(tokens);
    ZenCodingNode node = parser.parse();
    return parser.myIndex == tokens.size() - 1 ? node : null;
  }

  @Nullable
  private List<ZenCodingToken> lex(@NotNull String text, @NotNull CustomTemplateCallback callback) {
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
    List<ZenCodingToken> result = new ArrayList<ZenCodingToken>();
    for (int i = 0, n = text.length(); i < n; i++) {
      char c = text.charAt(i);
      if (inQuotes || inApostrophes) {
        templateKeyBuilder.append(c);
        if (c == '"') {
          inQuotes = false;
        }
        else if (c == '\'') inApostrophes = false;
      }
      else if (i == n - 1 || (i < n - 2 && DELIMS.indexOf(c) >= 0)) {
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
        if (i == n - 1) {
          result.add(new MarkerToken());
        }
        else if (c == '(') {
          result.add(new OpeningBraceToken());
        }
        else if (c == ')') {
          result.add(new ClosingBraceToken());
        }
        else {
          result.add(new OperationToken(c));
        }
      }
      else if (!Character.isWhitespace(c)) {
        templateKeyBuilder.append(c);
        if (c == '"') {
          inQuotes = true;
        }
        else if (c == '\'') inApostrophes = true;
      }
      else {
        return null;
      }
    }

    if (inQuotes || inApostrophes) {
      return null;
    }

    // at least MarkerToken
    assert result.size() > 0;

    if (filter != null) {
      result.add(result.size() - 1, new FilterToken(filter));
    }
    return result;
  }

  @Nullable
  protected abstract TemplateToken parseTemplateKey(String key, CustomTemplateCallback callback);

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
    return parse(key, callback) != null;
  }

  public void expand(String key, @NotNull CustomTemplateCallback callback) {
    expand(key, callback, null);
  }

  @Nullable
  private static ZenCodingGenerator findApplicableGenerator(ZenCodingNode node, PsiElement context) {
    ZenCodingGenerator defaultGenerator = null;
    List<ZenCodingGenerator> generators = ZenCodingGenerator.getInstances();
    for (ZenCodingGenerator generator : generators) {
      if (defaultGenerator == null && generator.isMyContext(context) && generator.isAppliedByDefault(context)) {
        defaultGenerator = generator;
      }
    }
    while (node instanceof FilterNode) {
      FilterNode filterNode = (FilterNode)node;
      String suffix = filterNode.getFilter();
      for (ZenCodingGenerator generator : generators) {
        if (generator.isMyContext(context)) {
          if (suffix != null && suffix.equals(generator.getSuffix())) {
            return generator;
          }
        }
      }
      node = filterNode.getNode();
    }
    return defaultGenerator;
  }

  private void expand(String key,
                      @NotNull CustomTemplateCallback callback,
                      String surroundedText) {
    ZenCodingNode node = parse(key, callback);
    assert node != null;
    if (surroundedText == null) {
      if (node instanceof TemplateNode) {
        if (key.equals(((TemplateNode)node).getTemplateToken().getKey()) && callback.findApplicableTemplates(key).size() > 1) {
          callback.startTemplate();
          return;
        }
      }
      callback.deleteTemplateKey(key);
    }

    ZenCodingGenerator generator = findApplicableGenerator(node, callback.getContext());

    List<GenerationNode> genNodes = node.expand(-1);
    LiveTemplateBuilder builder = new LiveTemplateBuilder();
    int end = -1;
    for (int i = 0, genNodesSize = genNodes.size(); i < genNodesSize; i++) {
      GenerationNode genNode = genNodes.get(i);
      TemplateImpl template = genNode.generate(callback, surroundedText, generator);
      int e = builder.insertTemplate(builder.length(), template, null);
      if (end == -1 && end < builder.length()) {
        end = e;
      }
    }
    if (end < builder.length()) {
      builder.insertVariableSegment(end, TemplateImpl.END);
    }
    callback.startTemplate(builder.buildTemplate(), null);
  }

  public void wrap(final String selection,
                   @NotNull final CustomTemplateCallback callback
  ) {
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
      doWrap(selection, abbreviation, callback);
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
                        final CustomTemplateCallback callback) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(callback.getProject(), new Runnable() {
          public void run() {
            EditorModificationUtil.deleteSelectedText(callback.getEditor());
            PsiDocumentManager.getInstance(callback.getProject()).commitAllDocuments();
            callback.fixInitialState();
            expand(abbreviation, callback, selection);
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

  protected static boolean containsAttrsVar(TemplateImpl template) {
    for (int i = 0; i < template.getVariableCount(); i++) {
      String varName = template.getVariableNameAt(i);
      if (ATTRS.equals(varName)) {
        return true;
      }
    }
    return false;
  }

  private static class MyParser {
    private final List<ZenCodingToken> myTokens;
    private int myIndex = 0;

    private MyParser(List<ZenCodingToken> tokens) {
      myTokens = tokens;
    }

    @Nullable
    private ZenCodingNode parse() {
      ZenCodingNode add = parseAddOrMore();
      if (add == null) {
        return null;
      }
      ZenCodingToken filter = nextToken();
      if (filter instanceof FilterToken) {
        myIndex++;
        return new FilterNode(add, ((FilterToken)filter).getSuffix());
      }
      return add;
    }

    @Nullable
    private ZenCodingNode parseAddOrMore() {
      ZenCodingNode mul = parseMul(true);
      if (mul == null) {
        return null;
      }
      ZenCodingToken operationToken = nextToken();
      if (!(operationToken instanceof OperationToken)) {
        return mul;
      }
      char sign = ((OperationToken)operationToken).getSign();
      if (sign == '+') {
        myIndex++;
        ZenCodingNode add2 = parseAddOrMore();
        if (add2 == null) {
          return null;
        }
        return new AddOperationNode(mul, add2);
      }
      else if (sign == '>') {
        myIndex++;
        ZenCodingNode more2 = parseAddOrMore();
        if (more2 == null) {
          return null;
        }
        return new MoreOperationNode(mul, more2);
      }
      return null;
    }

    @Nullable
    private ZenCodingNode parseMul(boolean canBeSingle) {
      ZenCodingNode exp = parseExpressionInBraces();
      if (exp == null) {
        return null;
      }
      ZenCodingToken operationToken = nextToken();
      if (!(operationToken instanceof OperationToken)) {
        return canBeSingle ? exp : null;
      }
      if (((OperationToken)operationToken).getSign() != '*') {
        return canBeSingle ? exp : null;
      }
      myIndex++;
      ZenCodingToken numberToken = nextToken();
      if (numberToken instanceof NumberToken) {
        myIndex++;
        return new MulOperationNode(exp, ((NumberToken)numberToken).getNumber());
      }
      return null;
    }

    @Nullable
    private ZenCodingNode parseExpressionInBraces() {
      ZenCodingToken openingBrace = nextToken();
      if (openingBrace instanceof OpeningBraceToken) {
        myIndex++;
        ZenCodingNode add = parseAddOrMore();
        if (add == null) {
          return null;
        }
        ZenCodingToken closingBrace = nextToken();
        if (!(closingBrace instanceof ClosingBraceToken)) {
          return null;
        }
        myIndex++;
        return add;
      }
      ZenCodingToken templateToken = nextToken();
      if (templateToken instanceof TemplateToken) {
        myIndex++;
        return new TemplateNode((TemplateToken)templateToken);
      }
      return null;
    }

    @Nullable
    private ZenCodingToken nextToken() {
      if (myIndex < myTokens.size()) {
        return myTokens.get(myIndex);
      }
      return null;
    }
  }
}
