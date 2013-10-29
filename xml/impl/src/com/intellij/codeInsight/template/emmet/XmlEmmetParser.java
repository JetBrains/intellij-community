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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.Stack;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.text.StringUtil.startsWithIgnoreCase;

/**
 * User: zolotov
 * Date: 2/7/13
 */
public class XmlEmmetParser extends EmmetParser {
  private static final String DEFAULT_TAG = "div";
  private static final int DEFAULT_LOREM_LENGTH = 30;
  private static final Pattern LOREM_PATTERN = Pattern.compile("(lorem|lipsum)(\\d*)");
  @NonNls private static final String DEFAULT_INLINE_TAG = "span";
  @NonNls private static final String LOREM_KEYWORD = "lorem";
  @NonNls private static final String LIPSUM_KEYWORD = "lipsum";
  private static final String ID = "id";
  private static final String CLASS = "class";

  private boolean hasTagContext = false;
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

  public XmlEmmetParser(List<ZenCodingToken> tokens,
                        CustomTemplateCallback callback,
                        ZenCodingGenerator generator, boolean surroundWithTemplate) {
    super(tokens, callback, generator);
    PsiElement context = callback.getContext();
    XmlTag parentTag = PsiTreeUtil.getParentOfType(context, XmlTag.class);
    if (surroundWithTemplate && parentTag != null && context.getNode().getElementType() == XmlTokenType.XML_START_TAG_START) {
      parentTag = PsiTreeUtil.getParentOfType(parentTag, XmlTag.class);
    }
    if (parentTag != null) {
      hasTagContext = true;
      tagLevel.push(parentTag.getName());
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

    final List<Pair<String, String>> attrList = parseSelectors();
    if (mustHaveSelector && attrList.size() == 0) {
      return null;
    }

    final TemplateToken templateToken = new TemplateToken(templateKey, attrList);
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
  protected ZenCodingNode parseMoreOperation(@NotNull ZenCodingNode leftPart) {
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
  @Override
  protected String getDefaultTemplateKey() {
    return ZenCodingUtil.isHtml(myCallback) ? suggestTagName() : null;
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
        loremWordsCount = group.isEmpty() ? DEFAULT_LOREM_LENGTH : Integer.parseInt(group);
      }

      final List<Pair<String, String>> attrList = parseSelectors();
      ZenCodingToken token = getToken();
      boolean isRepeating = token instanceof OperationToken && ((OperationToken)token).getSign() == '*';
      if (!attrList.isEmpty() || isRepeating) {
        String wrapTag = suggestTagName();
        TemplateImpl template = myCallback.findApplicableTemplate(templateKey);
        if (template == null && !ZenCodingUtil.isXML11ValidQName(templateKey)) {
          return null;
        }
        final TemplateToken templateToken = new TemplateToken(wrapTag, attrList);
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
    ZenCodingToken token = getToken();
    if (token == ZenCodingTokens.OPENING_SQ_BRACKET) {
      advance();
      final List<Pair<String, String>> attrList = parseAttributeList();
      if (attrList == null || getToken() != ZenCodingTokens.CLOSING_SQ_BRACKET) {
        return null;
      }
      advance();
      return attrList;
    }

    if (token == ZenCodingTokens.DOT || token == ZenCodingTokens.SHARP) {
      final String name = token == ZenCodingTokens.DOT ? CLASS : ID;
      advance();
      token = getToken();
      final String value = getAttributeValueByToken(token);
      if (!value.isEmpty()) {
        advance();
      }
      return Collections.singletonList(new Pair<String, String>(name, value));
    }

    return null;
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

      final ZenCodingToken token = getToken();
      if (token != ZenCodingTokens.COMMA && token != ZenCodingTokens.SPACE) {
        return result;
      }
      advance();
    }
  }

  @Nullable
  private Pair<String, String> parseAttribute() {
    ZenCodingToken token = getToken();
    if (!(token instanceof IdentifierToken)) {
      return null;
    }

    String name = ((IdentifierToken)token).getText();

    if (name.isEmpty()) {
      return null;
    }

    final XmlTag tag = XmlElementFactory.getInstance(myCallback.getProject()).createTagFromText("<tag " + name + "=''/>");
    XmlAttribute[] attributes = tag.getAttributes();
    if (attributes.length == 1) {
      name = attributes[0].getName();
    }
    else {
      return null;
    }

    advance();
    token = getToken();
    if (token != ZenCodingTokens.EQ) {
      return new Pair<String, String>(name, "");
    }

    advance();
    final StringBuilder attrValueBuilder = new StringBuilder();
    String value;
    do {
      token = getToken();
      value = getAttributeValueByToken(token);
      attrValueBuilder.append(value);
      if (token != null && token != ZenCodingTokens.CLOSING_SQ_BRACKET
          && token != ZenCodingTokens.SPACE && token != ZenCodingTokens.COMMA) {
        advance();
      }
    }
    while (token != null && token != ZenCodingTokens.CLOSING_SQ_BRACKET
           && token != ZenCodingTokens.SPACE && token != ZenCodingTokens.COMMA);
    return new Pair<String, String>(name, attrValueBuilder.toString());
  }
}
