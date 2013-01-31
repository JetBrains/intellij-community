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

import com.google.common.base.Strings;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.nodes.*;
import com.intellij.codeInsight.template.emmet.tokens.*;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.Stack;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: zolotov
 * Date: 1/25/13
 */
class EmmetParser {
  private static final String ID = "id";
  private static final String CLASS = "class";
  private static final String DEFAULT_TAG = "div";
  private final List<ZenCodingToken> myTokens;
  private final CustomTemplateCallback myCallback;
  private final ZenCodingGenerator myGenerator;
  private final Stack<String> tagLevel = new Stack<String>();

  private static Map<String, String> parentChildTagMapping = new HashMap<String, String>() {{
    put("p", "span");
    put("ul", "li");
    put("ol", "li");
    put("table", "tr");
    put("tr", "td");
    put("tbody", "tr");
    put("thead", "tr");
    put("tfoot", "tr");
    put("colgroup", "col");
    put("select", "option");
    put("optgroup", "option");
    put("audio", "source");
    put("video", "source");
    put("object", "param");
    put("map", "area");
  }};

  private int myIndex = 0;

  EmmetParser(List<ZenCodingToken> tokens, CustomTemplateCallback callback, ZenCodingGenerator generator) {
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
      ZenCodingToken token = nextToken();
      if (token != ZenCodingTokens.PIPE) {
        return result;
      }

      myIndex++;
      token = nextToken();
      if (!(token instanceof IdentifierToken)) {
        return null;
      }

      final String filterSuffix = ((IdentifierToken)token).getText();
      if (!ZenCodingUtil.checkFilterSuffix(filterSuffix)) {
        return null;
      }

      myIndex++;
      result = new FilterNode(result, filterSuffix);
    }
  }

  @Nullable
  private ZenCodingNode parseAddOrMore() {
    ZenCodingNode mul = parseMul();

    ZenCodingToken operationToken = nextToken();
    if (!(operationToken instanceof OperationToken)) {
      return mul;
    }
    char sign = ((OperationToken)operationToken).getSign();

    if (sign == '^') {
      myIndex++;
      mul = mul != null ? mul : ZenEmptyNode.INSTANCE;
      ZenCodingNode climbUp2 = parseAddOrMore();
      if(climbUp2 == null) {
        return null;
      }
      return new ClimbUpOperationNode(mul, climbUp2);
    }
    if (mul == null) {
      return null;
    }
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
      String parentTag = getParentTag(mul);
      boolean hasParent = false;
      if(!Strings.isNullOrEmpty(parentTag)) {
        hasParent = true;
        tagLevel.push(parentTag);
      }
      ZenCodingNode more2 = parseAddOrMore();
      if (more2 == null) {
        return null;
      }
      if(hasParent) {
        tagLevel.pop();
      }
      return new MoreOperationNode(mul, more2);
    }
    return null;
  }

  @Nullable
  private static String getParentTag(ZenCodingNode node) {
    if(node instanceof TemplateNode) {
      return ((TemplateNode)node).getTemplateToken().getKey();
    } else if(node instanceof MulOperationNode) {
      ZenCodingNode leftOperand = ((MulOperationNode)node).getLeftOperand();
      if(leftOperand instanceof TemplateNode) {
        return ((TemplateNode)leftOperand).getTemplateToken().getKey();
      }
    }
    return null;
  }

  @Nullable
  private ZenCodingNode parseMul() {
    ZenCodingNode exp = parseExpressionInBraces();
    if (exp == null) {
      return null;
    }
    ZenCodingToken operationToken = nextToken();
    if (!(operationToken instanceof OperationToken)) {
      return exp;
    }
    if (((OperationToken)operationToken).getSign() != '*') {
      return exp;
    }
    myIndex++;
    ZenCodingToken numberToken = nextToken();
    if (numberToken instanceof NumberToken) {
      myIndex++;
      return new MulOperationNode(exp, ((NumberToken)numberToken).getNumber());
    }
    return new UnaryMulOperationNode(exp);
  }

  @Nullable
  private ZenCodingNode parseExpressionInBraces() {
    ZenCodingToken token = nextToken();
    if (token == ZenCodingTokens.OPENING_R_BRACKET) {
      myIndex++;
      ZenCodingNode add = parseAddOrMore();
      if (add == null) {
        return null;
      }
      ZenCodingToken closingBrace = nextToken();
      if (closingBrace != ZenCodingTokens.CLOSING_R_BRACKET) {
        return null;
      }
      myIndex++;
      return add;
    }
    else if (token instanceof TextToken) {
      myIndex++;
      return new TextNode((TextToken)token);
    }

    final ZenCodingNode templateNode = parseTemplate();
    if (templateNode == null) {
      return null;
    }

    token = nextToken();
    if (token instanceof TextToken) {
      myIndex++;
      return new MoreOperationNode(templateNode, new TextNode((TextToken)token));
    }
    return templateNode;
  }

  @Nullable
  private ZenCodingNode parseTemplate() {
    final ZenCodingToken token = nextToken();
    String templateKey = ZenCodingUtil.isHtml(myCallback) ? suggestTagName() : null;
    boolean mustHaveSelector = true;

    if (token instanceof IdentifierToken) {
      templateKey = ((IdentifierToken)token).getText();
      mustHaveSelector = false;
      myIndex++;
    }

    if (templateKey == null) {
      return null;
    }

    final TemplateImpl template = myCallback.findApplicableTemplate(templateKey);
    if (template == null && !ZenCodingUtil.isXML11ValidQName(templateKey)) {
      return null;
    }

    final List<Pair<String, String>> attrList = parseSelectors();
    if (mustHaveSelector && attrList.size() == 0) {
      return null;
    }

    final TemplateToken templateToken = new TemplateToken(templateKey, attrList);

    if (!setTemplate(templateToken, template)) {
      return null;
    }
    return new TemplateNode(templateToken);
  }

  private String suggestTagName() {
    if(!tagLevel.empty()) {
      String parentTag = tagLevel.peek();
      if (parentChildTagMapping.containsKey(parentTag)) {
        return parentChildTagMapping.get(parentTag);
      }
      if (HtmlUtil.isPossiblyInlineTag(parentTag)) {
        return "span";
      }
    }
    return DEFAULT_TAG;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private List<Pair<String, String>> parseSelectors() {
    final List<Pair<String, String>> result = new ArrayList<Pair<String, String>>();

    int classAttrPosition = -1;
    int idAttrPosition = -1;

    final StringBuilder classAttrBuilder = new StringBuilder();
    final StringBuilder idAttrBuilder = new StringBuilder();

    while (true) {
      final List<Pair<String, String>> attrList = parseSelector();
      if (attrList == null) {
        if (classAttrPosition != -1) {
          result.set(classAttrPosition, new Pair<String, String>(CLASS, classAttrBuilder.toString()));
        }
        if (idAttrPosition != -1) {
          result.set(idAttrPosition, new Pair<String, String>(ID, idAttrBuilder.toString()));
        }
        return result;
      }

      for (Pair<String, String> attr : attrList) {
        if (CLASS.equals(attr.first)) {
          if (classAttrBuilder.length() > 0) {
            classAttrBuilder.append(' ');
          }
          classAttrBuilder.append(attr.second);
          if (classAttrPosition == -1) {
            classAttrPosition = result.size();
            result.add(attr);
          }
        }
        else if (ID.equals(attr.first)) {
          if (idAttrBuilder.length() > 0) {
            idAttrBuilder.append(' ');
          }
          idAttrBuilder.append(attr.second);
          if (idAttrPosition == -1) {
            idAttrPosition = result.size();
            result.add(attr);
          }
        }
        else {
          result.add(attr);
        }
      }
    }
  }

  @Nullable
  private List<Pair<String, String>> parseSelector() {
    ZenCodingToken token = nextToken();
    if (token == ZenCodingTokens.OPENING_SQ_BRACKET) {
      myIndex++;
      final List<Pair<String, String>> attrList = parseAttributeList();
      if (attrList == null || nextToken() != ZenCodingTokens.CLOSING_SQ_BRACKET) {
        return null;
      }
      myIndex++;
      return attrList;
    }

    if (token == ZenCodingTokens.DOT || token == ZenCodingTokens.SHARP) {
      final String name = token == ZenCodingTokens.DOT ? CLASS : ID;
      myIndex++;
      token = nextToken();
      final String value = getAttributeValueByToken(token);
      if (!value.isEmpty()) {
        myIndex++;
      }
      return Collections.singletonList(new Pair<String, String>(name, value));
    }

    return null;
  }

  private boolean setTemplate(final TemplateToken token, TemplateImpl template) {
    if (template == null) {
      template = myGenerator.createTemplateByKey(token.getKey());
    }
    if (template == null) {
      return false;
    }
    return ZenCodingTemplate.doSetTemplate(token, template, myCallback);
  }

  @Nullable
  private List<Pair<String, String>> parseAttributeList() {
    final List<Pair<String, String>> result = new ArrayList<Pair<String, String>>();
    while (true) {
      final Pair<String, String> attribute = parseAttribute();
      if (attribute == null) {
        return result;
      }
      result.add(attribute);

      final ZenCodingToken token = nextToken();
      if (token != ZenCodingTokens.COMMA && token != ZenCodingTokens.SPACE) {
        return result;
      }
      myIndex++;
    }
  }

  @Nullable
  private Pair<String, String> parseAttribute() {
    ZenCodingToken token = nextToken();
    if (!(token instanceof IdentifierToken)) {
      return null;
    }

    final String name = ((IdentifierToken)token).getText();

    myIndex++;
    token = nextToken();
    if (token != ZenCodingTokens.EQ) {
      return new Pair<String, String>(name, "");
    }

    myIndex++;
    final StringBuilder attrValueBuilder = new StringBuilder();
    String value;
    do {
      token = nextToken();
      value = token != null && token == ZenCodingTokens.SHARP ? token.toString() : getAttributeValueByToken(token);
      attrValueBuilder.append(value);
      if (token != null && token != ZenCodingTokens.CLOSING_SQ_BRACKET
          && token != ZenCodingTokens.SPACE && token != ZenCodingTokens.COMMA) {
        myIndex++;
      }
    }
    while (token != null && token != ZenCodingTokens.CLOSING_SQ_BRACKET
           && token != ZenCodingTokens.SPACE && token != ZenCodingTokens.COMMA);
    return new Pair<String, String>(name, attrValueBuilder.toString());
  }

  @NotNull
  private static String getAttributeValueByToken(ZenCodingToken token) {
    if (token instanceof StringLiteralToken) {
      final String text = ((StringLiteralToken)token).getText();
      return text.substring(1, text.length() - 1);
    }
    else if (token instanceof IdentifierToken) {
      return ((IdentifierToken)token).getText();
    }
    else if (token instanceof NumberToken) {
      return Integer.toString(((NumberToken)token).getNumber());
    }
    else if (token == ZenCodingTokens.DOT) {
      return token.toString();
    }
    return "";
  }

  @Nullable
  private ZenCodingToken nextToken() {
    if (myIndex < myTokens.size()) {
      return myTokens.get(myIndex);
    }
    return null;
  }
}
