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
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.nodes.*;
import com.intellij.codeInsight.template.emmet.tokens.*;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
      if (ZenCodingUtil.checkFilterSuffix(filterSuffix)) {
        result = new FilterNode(result, filterSuffix);
      }

      advance();
    }
  }

  @Nullable
  protected ZenCodingNode parseAddOrMore() {
    ZenCodingNode mul = parseMul(parseExpression());

    ZenCodingToken operationToken = getToken();
    if (operationToken == ZenCodingTokens.OPENING_R_BRACKET) {
      mul = parseMul(new MoreOperationNode(notNullNode(mul), notNullNode(parseExpression())));
      operationToken = getToken();
    }
    if (!(operationToken instanceof OperationToken)) {
      return mul;
    }
    char sign = ((OperationToken)operationToken).getSign();

    if (sign == '^') {
      return parseClimbUpOperation(mul);
    }
    if (sign == '+') {
      advance();
      ZenCodingNode add2 = parseAddOrMore();
      if (add2 == null) {
        return mul;
      }
      return new AddOperationNode(notNullNode(mul), add2);
    }
    if (sign == '>') {
      return parseMoreOperation(mul);
    }
    return null;
  }

  protected ZenCodingNode parseClimbUpOperation(@Nullable ZenCodingNode leftPart) {
    advance();
    leftPart = notNullNode(leftPart);
    ZenCodingNode rightPart = parseAddOrMore();
    if (rightPart == null) {
      return leftPart;
    }
    return new ClimbUpOperationNode(leftPart, rightPart);
  }

  protected ZenCodingNode parseMoreOperation(@Nullable ZenCodingNode leftPart) {
    advance();
    leftPart = notNullNode(leftPart);
    ZenCodingNode rightPart = parseAddOrMore();
    if (rightPart == null) {
      return leftPart;
    }
    return new MoreOperationNode(leftPart, rightPart);
  }

  private static ZenCodingNode notNullNode(ZenCodingNode node) {
    return node != null ? node : ZenEmptyNode.INSTANCE;
  }

  protected int advance() {
    return myIndex++;
  }

  @Nullable
  private ZenCodingNode parseMul(@Nullable ZenCodingNode expression) {
    ZenCodingToken operationToken = getToken();
    if (expression != null && operationToken instanceof OperationToken && ((OperationToken)operationToken).getSign() == '*') {
      advance();
      ZenCodingToken numberToken = getToken();
      if (numberToken instanceof NumberToken) {
        advance();
        return new MulOperationNode(expression, ((NumberToken)numberToken).getNumber());
      }
      return new UnaryMulOperationNode(expression);
    }
    return expression;
  }

  @Nullable
  private ZenCodingNode parseExpression() {
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

  protected boolean setTemplate(final TemplateToken token, TemplateImpl template) {
    if (template == null) {
      template = myGenerator.createTemplateByKey(token.getKey(), token.isForceSingleTag());
    }
    if (template == null) {
      return false;
    }
    token.setTemplate(template, myCallback);
    return true;
  }

  @Nullable
  protected ZenCodingToken getToken() {
    if (myIndex < myTokens.size()) {
      return myTokens.get(myIndex);
    }
    return null;
  }
  
  
  @Nullable
  protected ZenCodingToken nextToken(int i) {
    if (myIndex + i < myTokens.size()) {
      return myTokens.get(myIndex + i);
    }
    return null;
  }

  protected int getCurrentPosition() {
    return myIndex;
  }

  protected void restorePosition(int position) {
    myIndex = position;
  }
}
