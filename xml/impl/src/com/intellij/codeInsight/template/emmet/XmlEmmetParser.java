/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.openapi.util.text.StringUtil.startsWithIgnoreCase;

public class XmlEmmetParser extends EmmetParser {
  public static final String DEFAULT_ATTRIBUTE_NAME = "%default";
  public static final String BOOLEAN_ATTRIBUTE_VALUE = "%boolean";
  
  private static final String DEFAULT_TAG = "div";
  private static final int DEFAULT_LOREM_LENGTH = 30;
  private static final Pattern LOREM_PATTERN = Pattern.compile("(lorem|lipsum)(\\d*)");
  @NonNls private static final String DEFAULT_INLINE_TAG = "span";
  @NonNls private static final String LOREM_KEYWORD = "lorem";
  @NonNls private static final String LIPSUM_KEYWORD = "lipsum";

  private boolean hasTagContext = false;
  private final Stack<String> tagLevel = new Stack<>();

  private static final Map<String, String> parentChildTagMapping = ContainerUtil.newHashMap(
    pair("p", "span"),
    pair("ul", "li"),
    pair("ol", "li"),
    pair("table", "tr"),
    pair("tr", "td"),
    pair("tbody", "tr"),
    pair("thead", "tr"),
    pair("tfoot", "tr"),
    pair("colgroup", "col"),
    pair("select", "option"),
    pair("optgroup", "option"),
    pair("audio", "source"),
    pair("video", "source"),
    pair("object", "param"),
    pair("map", "area"));

  private boolean isHtml;

  public XmlEmmetParser(List<ZenCodingToken> tokens,
                        CustomTemplateCallback callback,
                        ZenCodingGenerator generator, boolean surroundWithTemplate) {
    super(tokens, callback, generator);
    PsiElement context = callback.getContext();
    XmlTag parentTag = PsiTreeUtil.getParentOfType(context, XmlTag.class);
    if (surroundWithTemplate && parentTag != null && context.getNode().getElementType() == XmlTokenType.XML_START_TAG_START) {
      parentTag = PsiTreeUtil.getParentOfType(parentTag, XmlTag.class);
    }
    isHtml = generator.isHtml(callback);
    if (parentTag != null) {
      hasTagContext = true;
      tagLevel.push(parentTag.getName());
    }
  }

  @Nullable
  private String parseAttributeName() {
    String name = "";
    ZenCodingToken token = getToken();
    while (token != null) {
      if ((token instanceof IdentifierToken)) {
        name += ((IdentifierToken)token).getText();
      }
      else if (token instanceof OperationToken && 
               (((OperationToken)token).getSign() == '+' || ((OperationToken)token).getSign() == '-')) {
        name += ((OperationToken)token).getSign();
      }
      else {
        break;
      }
      advance();
      token = getToken();
    }

    if (name.isEmpty()) {
      return null;
    }

    final XmlTag tag = XmlElementFactory.getInstance(myCallback.getProject()).createTagFromText("<tag " + name + "=''/>", StdLanguages.HTML);
    XmlAttribute[] attributes = tag.getAttributes();
    if (attributes.length == 1) {
      return attributes[0].getName();
    }
    else {
      return null;
    }
  }
  
  @NotNull
  private static String getAttributeValueByToken(@Nullable ZenCodingToken token) {
    if (token == null) {
      return "";
    }
    if (token instanceof StringLiteralToken) {
      final String text = ((StringLiteralToken)token).getText();
      return text.substring(1, text.length() - 1);
    }
    else if (token instanceof TextToken) {
      return ((TextToken)token).getText();
    }
    else if (token instanceof IdentifierToken) {
      return ((IdentifierToken)token).getText();
    }
    else if (token instanceof NumberToken) {
      return Integer.toString(((NumberToken)token).getNumber());
    }
    else if (token == ZenCodingTokens.DOT || token == ZenCodingTokens.SHARP) {
      return token.toString();
    }
    return "";
  }

  @Nullable
  @Override
  protected ZenCodingNode parseTemplate() {
    ZenCodingToken token = getToken();
    String templateKey = getDefaultTemplateKey();
    boolean mustHaveSelector = true;

    if (token instanceof IdentifierToken) {
      templateKey = ((IdentifierToken)token).getText();
      advance();
      if (startsWithIgnoreCase(templateKey, LOREM_KEYWORD) || startsWithIgnoreCase(templateKey, LIPSUM_KEYWORD)) {
        return parseLorem(templateKey);
      }
      mustHaveSelector = false;
    }

    if (templateKey == null) {
      return null;
    }

    TemplateImpl template = myCallback.findApplicableTemplate(templateKey);
    if (template == null && !ZenCodingUtil.isXML11ValidQName(templateKey) && !StringUtil.containsChar(templateKey, '$')) {
      return null;
    }

    final Map<String, String> attributes = parseSelectors();
    if (mustHaveSelector && attributes.isEmpty()) {
      return null;
    }

    final TemplateToken templateToken = new TemplateToken(templateKey, attributes);
    if (!setTemplate(templateToken, template)) {
      return null;
    }
    return new TemplateNode(templateToken, myGenerator);
  }

  @Override
  protected ZenCodingNode parseClimbUpOperation(@Nullable ZenCodingNode leftPart) {
    popTagLevel();
    return super.parseClimbUpOperation(leftPart);
  }

  @Override
  protected ZenCodingNode parseMoreOperation(@Nullable ZenCodingNode leftPart) {
    String parentTag = getParentTag(leftPart);
    boolean hasParent = false;
    if (!Strings.isNullOrEmpty(parentTag)) {
      hasParent = true;
      tagLevel.push(parentTag);
    }
    ZenCodingNode result = super.parseMoreOperation(leftPart);
    if (result == null) {
      return null;
    }
    if (hasParent) {
      popTagLevel();
    }
    return result;
  }

  @Nullable
  private String getDefaultTemplateKey() {
    return isHtml ? suggestTagName() : null;
  }

  @Nullable
  private static String getParentTag(ZenCodingNode node) {
    if (node instanceof TemplateNode) {
      return ((TemplateNode)node).getTemplateToken().getKey();
    }
    else if (node instanceof MulOperationNode) {
      ZenCodingNode leftOperand = ((MulOperationNode)node).getLeftOperand();
      if (leftOperand instanceof TemplateNode) {
        return ((TemplateNode)leftOperand).getTemplateToken().getKey();
      }
    }
    return null;
  }

  @Nullable
  private ZenCodingNode parseLorem(String templateKey) {
    Matcher matcher = LOREM_PATTERN.matcher(templateKey);
    if (matcher.matches()) {
      int loremWordsCount = DEFAULT_LOREM_LENGTH;
      if (matcher.groupCount() > 1) {
        String group = matcher.group(2);
        loremWordsCount = group == null || group.isEmpty() ? DEFAULT_LOREM_LENGTH : Integer.parseInt(group);
      }

      final Map<String, String> attributes = parseSelectors();
      ZenCodingToken token = getToken();
      boolean isRepeating = token instanceof OperationToken && ((OperationToken)token).getSign() == '*';
      if (!attributes.isEmpty() || isRepeating) {
        String wrapTag = suggestTagName();
        TemplateImpl template = myCallback.findApplicableTemplate(templateKey);
        if (template == null && !ZenCodingUtil.isXML11ValidQName(templateKey)) {
          return null;
        }
        final TemplateToken templateToken = new TemplateToken(wrapTag, attributes);
        if (!setTemplate(templateToken, template)) {
          return null;
        }
        return new MoreOperationNode(new TemplateNode(templateToken), new LoremNode(loremWordsCount));
      }
      else {
        return new LoremNode(loremWordsCount);
      }
    }
    else {
      return null;
    }
  }

  private String suggestTagName() {
    if (!tagLevel.empty()) {
      String parentTag = tagLevel.peek();
      if (parentChildTagMapping.containsKey(parentTag)) {
        return parentChildTagMapping.get(parentTag);
      }
      if (HtmlUtil.isPossiblyInlineTag(parentTag)) {
        return DEFAULT_INLINE_TAG;
      }
    }
    return DEFAULT_TAG;
  }

  private void popTagLevel() {
    if (tagLevel.size() > (hasTagContext ? 1 : 0)) {
      tagLevel.pop();
    }
  }

  @NotNull
  private Map<String, String> parseSelectors() {
    final Map<String, String> result = ContainerUtil.newLinkedHashMap();
    List<Couple<String>> attrList = parseSelector();
    while (attrList != null) {
      for (Couple<String> attr : attrList) {
        if (getClassAttributeName().equals(attr.first)) {
          result.put(getClassAttributeName(), (StringUtil.notNullize(result.get(getClassAttributeName())) + " " + attr.second).trim());
        }
        else if (HtmlUtil.ID_ATTRIBUTE_NAME.equals(attr.first)) {
          result.put(HtmlUtil.ID_ATTRIBUTE_NAME, (StringUtil.notNullize(result.get(HtmlUtil.ID_ATTRIBUTE_NAME)) + " " + attr.second).trim());
        }
        else {
          result.put(attr.first, attr.second);
        }
      }
      attrList = parseSelector();
    }
    return result;
  }

  @Nullable
  private List<Couple<String>> parseSelector() {
    ZenCodingToken token = getToken();
    if (token == ZenCodingTokens.OPENING_SQ_BRACKET) {
      advance();
      final List<Couple<String>> attrList = parseAttributeList();
      if (attrList == null || getToken() != ZenCodingTokens.CLOSING_SQ_BRACKET) {
        return null;
      }
      advance();
      return attrList;
    }

    if (token == ZenCodingTokens.DOT || token == ZenCodingTokens.SHARP) {
      final String name = token == ZenCodingTokens.DOT ? getClassAttributeName() : HtmlUtil.ID_ATTRIBUTE_NAME;
      advance();
      token = getToken();
      final String value = getAttributeValueByToken(token);
      if (!value.isEmpty()) {
        advance();
      }
      return Collections.singletonList(Couple.of(name, value));
    }

    return null;
  }

  @NotNull
  protected String getClassAttributeName() {
    return HtmlUtil.CLASS_ATTRIBUTE_NAME;
  }

  @Nullable
  private List<Couple<String>> parseAttributeList() {
    final List<Couple<String>> result = new ArrayList<>();
    while (true) {
      final Couple<String> attribute = parseAttribute();
      if (attribute == null) {
        return result;
      }
      result.add(attribute);

      final ZenCodingToken token = getToken();
      if (token != ZenCodingTokens.COMMA && token != ZenCodingTokens.SPACE) {
        return result;
      }
      advance();
    }
  }

  @Nullable
  private Couple<String> parseAttribute() {
    final int position = getCurrentPosition();
    String attributeName = parseAttributeName();
    if (attributeName != null && !attributeName.isEmpty()) {
      if (getToken() == ZenCodingTokens.DOT) {
        if (isEndOfAttribute(nextToken(1))) {
          // boolean attribute
          advance(); // dot
          return Couple.of(attributeName, BOOLEAN_ATTRIBUTE_VALUE);
        }
      }
      else {
        // attribute with value
        if (getToken() == ZenCodingTokens.EQ) {
          advance();
          return Couple.of(attributeName, parseAttributeValue());
        }
        else {
          return Couple.of(attributeName, "");
        }
      }
    }
    restorePosition(position);

    final String impliedValue = parseAttributeValue();
    if (!impliedValue.isEmpty()) {
      // implied attribute
      return Couple.of(DEFAULT_ATTRIBUTE_NAME, impliedValue);
    }
    return null;
  }

  @NotNull
  private String parseAttributeValue() {
    ZenCodingToken token;
    final StringBuilder attrValueBuilder = new StringBuilder();
    String value;
    do {
      token = getToken();
      value = getAttributeValueByToken(token);
      attrValueBuilder.append(value);
      if (!isEndOfAttribute(token)) {
        advance();
      }
    }
    while (!isEndOfAttribute(token));
    return attrValueBuilder.toString();
  }

  private static boolean isEndOfAttribute(@Nullable ZenCodingToken nextToken) {
    return nextToken == null || nextToken == ZenCodingTokens.SPACE || nextToken == ZenCodingTokens.CLOSING_SQ_BRACKET 
           || nextToken == ZenCodingTokens.COMMA;
  }
}
