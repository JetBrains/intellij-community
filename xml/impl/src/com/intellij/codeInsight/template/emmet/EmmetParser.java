/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.nodes.*;
import com.intellij.codeInsight.template.emmet.tokens.*;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: zolotov
 * Date: 1/25/13
 */
public abstract class EmmetParser {
  private final List<ZenCodingToken> myTokens;
  protected final CustomTemplateCallback myCallback;
  protected final ZenCodingGenerator myGenerator;

  private int myIndex = 0;

  public EmmetParser(List<ZenCodingToken> tokens, CustomTemplateCallback callback, ZenCodingGenerator generator) {
    myTokens = tokens;
    myCallback = callback;
    myGenerator = generator;
  }

  public int getIndex() {
    return myIndex;
  }

  @Nullable
  public ZenCodingNode parse() {
    ZenCodingNode add = parseAddOrMore();
    if (add == null) {
      return null;
    }

    ZenCodingNode result = add;

    while (true) {
      ZenCodingToken token = getToken();
      if (token != ZenCodingTokens.PIPE) {
        return result;
      }

      advance();
      token = getToken();
      if (!(token instanceof IdentifierToken)) {
        return null;
      }

      final String filterSuffix = ((IdentifierToken)token).getText();
      if (!ZenCodingUtil.checkFilterSuffix(filterSuffix)) {
        return null;
      }

      advance();
      result = new FilterNode(result, filterSuffix);
    }
  }

  @Nullable
  protected ZenCodingNode parseAddOrMore() {
    ZenCodingNode mul = parseMul();

    ZenCodingToken operationToken = getToken();
    if (!(operationToken instanceof OperationToken)) {
      return mul;
    }
    char sign = ((OperationToken)operationToken).getSign();

    if (sign == '^') {
      return parseClimbUpOperation(mul);
    }
    if (mul == null) {
      return null;
    }
    if (sign == '+') {
      advance();
      ZenCodingNode add2 = parseAddOrMore();
      if (add2 == null) {
        return null;
      }
      return new AddOperationNode(mul, add2);
    }
    else if (sign == '>') {
      return parseMoreOperation(mul);
    }
    return null;
  }

  protected ZenCodingNode parseClimbUpOperation(@Nullable ZenCodingNode leftPart) {
    advance();
    leftPart = leftPart != null ? leftPart : ZenEmptyNode.INSTANCE;
    ZenCodingNode rigthPart = parseAddOrMore();
    if (rigthPart == null) {
      return null;
    }
    return new ClimbUpOperationNode(leftPart, rigthPart);
  }

  protected ZenCodingNode parseMoreOperation(@NotNull ZenCodingNode leftPart) {
    advance();
    ZenCodingNode rightPart = parseAddOrMore();
    if (rightPart == null) {
      return null;
    }
    return new MoreOperationNode(leftPart, rightPart);
  }

  protected int advance() {
    return myIndex++;
  }

  @Nullable
  private ZenCodingNode parseMul() {
    ZenCodingNode exp = parseExpressionInBraces();
    if (exp == null) {
      return null;
    }
    ZenCodingToken operationToken = getToken();
    if (!(operationToken instanceof OperationToken)) {
      return exp;
    }
    if (((OperationToken)operationToken).getSign() != '*') {
      return exp;
    }
    advance();
    ZenCodingToken numberToken = getToken();
    if (numberToken instanceof NumberToken) {
      advance();
      return new MulOperationNode(exp, ((NumberToken)numberToken).getNumber());
    }
    return new UnaryMulOperationNode(exp);
  }

  @Nullable
  private ZenCodingNode parseExpressionInBraces() {
    ZenCodingToken token = getToken();
    if (token == ZenCodingTokens.OPENING_R_BRACKET) {
      advance();
      ZenCodingNode add = parseAddOrMore();
      if (add == null) {
        return null;
      }
      ZenCodingToken closingBrace = getToken();
      if (closingBrace != ZenCodingTokens.CLOSING_R_BRACKET) {
        return null;
      }
      advance();
      return add;
    }
    else if (token instanceof TextToken) {
      advance();
      return new TextNode((TextToken)token);
    }

    final ZenCodingNode templateNode = parseTemplate();
    if (templateNode == null) {
      return null;
    }

    token = getToken();
    if (token instanceof TextToken) {
      advance();
      return new MoreOperationNode(templateNode, new TextNode((TextToken)token));
    }
    return templateNode;
  }

  @Nullable
  protected ZenCodingNode parseTemplate() {
    ZenCodingToken token = getToken();
    if (!(token instanceof IdentifierToken)) {
      return null;
    }
    String templateKey = ((IdentifierToken)token).getText();
    advance();

    TemplateImpl template = myCallback.findApplicableTemplate(templateKey);
    if (template == null && !ZenCodingUtil.isXML11ValidQName(templateKey)) {
      return null;
    }

    final TemplateToken templateToken = new TemplateToken(templateKey);
    if (!setTemplate(templateToken, template)) {
      return null;
    }
    return new TemplateNode(templateToken);
  }

  @Nullable
  protected String getDefaultTemplateKey() {
    return null;
  }

  protected boolean setTemplate(final TemplateToken token, TemplateImpl template) {
    if (template == null) {
      template = myGenerator.createTemplateByKey(token.getKey());
    }
    if (template == null) {
      return false;
    }
    return token.setTemplate(template, myCallback);
  }

  @Nullable
  protected ZenCodingToken getToken() {
    if (myIndex < myTokens.size()) {
      return myTokens.get(myIndex);
    }
    return null;
  }
}
