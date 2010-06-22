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

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.IntArrayList;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
class XmlZenCodingInterpreter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.zencoding.XmlZenCodingInterpreter");
  private static final String ATTRS = "ATTRS";

  private final List<Token> myTokens;

  private final CustomTemplateCallback myCallback;
  private final String mySurroundedText;

  private State myState;

  private XmlZenCodingInterpreter(List<Token> tokens,
                                  CustomTemplateCallback callback,
                                  State initialState,
                                  String surroundedText) {
    myTokens = tokens;
    myCallback = callback;
    mySurroundedText = surroundedText;
    myState = initialState;
  }

  private void finish() {
    myCallback.gotoEndOffset();
  }

  private void gotoChild(Object templateBoundsKey) {
    int startOfTemplate = myCallback.getStartOfTemplate(templateBoundsKey);
    int endOfTemplate = myCallback.getEndOfTemplate(templateBoundsKey);
    int offset = myCallback.getOffset();

    PsiFile file = myCallback.parseCurrentText(StdFileTypes.XML);

    PsiElement element = file.findElementAt(offset);
    if (offset < endOfTemplate && element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_END_TAG_START) {
      return;
    }

    int newOffset = -1;
    XmlTag tag = PsiTreeUtil.findElementOfClassAtRange(file, startOfTemplate, endOfTemplate, XmlTag.class);
    if (tag != null) {
      for (PsiElement child : tag.getChildren()) {
        if (child instanceof XmlToken && ((XmlToken)child).getTokenType() == XmlTokenType.XML_END_TAG_START) {
          newOffset = child.getTextOffset();
        }
      }
    }

    if (newOffset >= 0) {
      if (offset < endOfTemplate) {
        myCallback.fixEndOffset();
      }
      myCallback.moveToOffset(newOffset);
    }
  }

  // returns if expanding finished

  public static void interpret(List<Token> tokens,
                               int startIndex,
                               CustomTemplateCallback callback,
                               State initialState,
                               String surroundedText) {
    XmlZenCodingInterpreter interpreter =
      new XmlZenCodingInterpreter(tokens, callback, initialState, surroundedText);
    interpreter.invoke(startIndex);
  }

  private void invoke(int startIndex) {
    String filter = null;

    if (myTokens.size() > 0) {
      Token lastToken = myTokens.get(myTokens.size() - 1);
      if (lastToken instanceof FilterToken) {
        filter = ((FilterToken)lastToken).getSuffix();
      }
    }

    final int n = myTokens.size();
    TemplateToken templateToken = null;
    int number = -1;
    for (int i = startIndex; i < n; i++) {
      Token token = myTokens.get(i);
      switch (myState) {
        case OPERATION:
          if (templateToken != null) {
            if (token instanceof MarkerToken || token instanceof OperationToken) {
              final char sign = token instanceof OperationToken ? ((OperationToken)token).getSign() : ZenCodingTemplate.MARKER;
              if (sign == '+' || (mySurroundedText == null && sign == ZenCodingTemplate.MARKER)) {
                final Object key = new Object();
                myCallback.fixStartOfTemplate(key);
                invokeTemplate(templateToken, myCallback, 0, filter);
                myState = State.WORD;
                if (myCallback.getOffset() != myCallback.getEndOfTemplate(key)) {
                  myCallback.fixEndOffset();
                }
                if (sign == '+') {
                  myCallback.gotoEndOfTemplate(key);
                }
                templateToken = null;
              }
              else if (sign == '>' || (mySurroundedText != null && sign == ZenCodingTemplate.MARKER)) {
                startTemplateAndGotoChild(templateToken, filter);
                templateToken = null;
              }
              else if (sign == '*') {
                myState = State.NUMBER;
              }
            }
            else {
              fail();
            }
          }
          break;
        case WORD:
          if (token instanceof TemplateToken) {
            templateToken = ((TemplateToken)token);
            myState = State.OPERATION;
          }
          else {
            fail();
          }
          break;
        case NUMBER:
          if (token instanceof NumberToken) {
            number = ((NumberToken)token).getNumber();
            myState = State.AFTER_NUMBER;
          }
          else {
            fail();
          }
          break;
        case AFTER_NUMBER:
          if (token instanceof MarkerToken || token instanceof OperationToken) {
            char sign = token instanceof OperationToken ? ((OperationToken)token).getSign() : ZenCodingTemplate.MARKER;
            if (sign == '+' || (mySurroundedText == null && sign == ZenCodingTemplate.MARKER)) {
              invokeTemplateSeveralTimes(templateToken, 0, number, filter);
              templateToken = null;
            }
            else if (number > 1) {
              invokeTemplateAndProcessTail(templateToken, 0, number, i + 1, filter);
              return;
            }
            else {
              assert number == 1;
              startTemplateAndGotoChild(templateToken, filter);
              templateToken = null;
            }
            myState = State.WORD;
          }
          else {
            fail();
          }
          break;
      }
    }
    if (mySurroundedText != null) {
      insertText(myCallback, mySurroundedText);
    }
    finish();
  }

  private void startTemplateAndGotoChild(TemplateToken templateToken, String filter) {
    final Object key = new Object();
    myCallback.fixStartOfTemplate(key);
    invokeTemplate(templateToken, myCallback, 0, filter);
    myState = State.WORD;
    gotoChild(key);
  }

  private void invokeTemplateSeveralTimes(final TemplateToken templateToken,
                                          final int startIndex,
                                          final int count,
                                          String filter) {
    final Object key = new Object();
    myCallback.fixStartOfTemplate(key);
    for (int i = startIndex; i < count; i++) {
      invokeTemplate(templateToken, myCallback, i, filter);
      myState = State.WORD;
      if (myCallback.getOffset() != myCallback.getEndOfTemplate(key)) {
        myCallback.fixEndOffset();
      }
      myCallback.gotoEndOfTemplate(key);
    }
  }

  private static void insertText(CustomTemplateCallback callback, String text) {
    int offset = callback.getOffset();
    callback.insertString(offset, text);
  }

  private void invokeTemplateAndProcessTail(final TemplateToken templateToken,
                                            final int startIndex,
                                            final int count,
                                            final int tailStart,
                                            String filter) {
    final Object key = new Object();
    myCallback.fixStartOfTemplate(key);
    for (int i = startIndex; i < count; i++) {
      Object iterKey = new Object();
      myCallback.fixStartOfTemplate(iterKey);
      invokeTemplate(templateToken, myCallback, i, filter);
      gotoChild(iterKey);
      interpret(myTokens, tailStart, myCallback, State.WORD, mySurroundedText);
      if (myCallback.getOffset() != myCallback.getEndOfTemplate(key)) {
        myCallback.fixEndOffset();
      }
      myCallback.gotoEndOfTemplate(key);
    }
    finish();
  }

  static boolean containsAttrsVar(TemplateImpl template) {
    for (int i = 0; i < template.getVariableCount(); i++) {
      String varName = template.getVariableNameAt(i);
      if (ATTRS.equals(varName)) {
        return true;
      }
    }
    return false;
  }

  private static void removeVariablesWhichHasNoSegment(TemplateImpl template) {
    Set<String> segments = new HashSet<String>();
    for (int i = 0; i < template.getSegmentsCount(); i++) {
      segments.add(template.getSegmentName(i));
    }
    IntArrayList varsToRemove = new IntArrayList();
    for (int i = 0; i < template.getVariableCount(); i++) {
      String varName = template.getVariableNameAt(i);
      if (!segments.contains(varName)) {
        varsToRemove.add(i);
      }
    }
    for (int i = 0; i < varsToRemove.size(); i++) {
      template.removeVariable(varsToRemove.get(i));
    }
  }

  @Nullable
  private static Map<String, String> buildPredefinedValues(List<Pair<String, String>> attribute2value,
                                                           int numberInIteration,
                                                           CustomTemplateCallback callback) {
    String attributes = buildAttributesString(attribute2value, numberInIteration, callback);
    assert attributes != null;
    attributes = attributes.length() > 0 ? ' ' + attributes : null;
    Map<String, String> predefinedValues = null;
    if (attributes != null) {
      predefinedValues = new HashMap<String, String>();
      predefinedValues.put(ATTRS, attributes);
    }
    return predefinedValues;
  }

  @Nullable
  private static String buildAttributesString(List<Pair<String, String>> attribute2value,
                                              int numberInIteration,
                                              CustomTemplateCallback callback) {
    PsiElement context = callback.getContext();
    for (ZenCodingFilter filter : ZenCodingFilter.EP_NAME.getExtensions()) {
      if (filter.isMyContext(context)) {
        return filter.buildAttributesString(attribute2value, numberInIteration);
      }
    }
    return new XmlZenCodingFilterImpl().buildAttributesString(attribute2value, numberInIteration);
  }


  private static void invokeTemplate(TemplateToken token,
                                     final CustomTemplateCallback callback,
                                     final int numberInIteration,
                                     String filter) {
    if (token instanceof XmlTemplateToken && token.getTemplate() != null) {
      XmlTemplateToken xmlTemplateToken = (XmlTemplateToken)token;
      final List<Pair<String, String>> attr2value = new ArrayList<Pair<String, String>>(xmlTemplateToken.getAttribute2Value());
      TemplateImpl modifiedTemplate = token.getTemplate().copy();
      final XmlTag tag = xmlTemplateToken.getTag();
      if (tag != null) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            setAttributeValues(tag, attr2value, numberInIteration);
          }
        });
        String s = filterXml(tag, callback, filter);
        assert s != null;
        if (HtmlUtil.isHtmlBlockTagL(tag.getName())) {
          boolean newLineBefore = callback.newLineBefore();
          boolean newLineAfter = callback.newLineAfter();
          if (!newLineBefore || !newLineAfter) {
            StringBuilder builder = new StringBuilder();
            if (!newLineBefore) {
              builder.append('\n');
            }
            builder.append(s);
            if (!newLineAfter) {
              builder.append('\n');
            }
            s = builder.toString();
          }
        }
        modifiedTemplate.setString(s);
        removeVariablesWhichHasNoSegment(modifiedTemplate);
        Map<String, String> predefinedValues = buildPredefinedValues(attr2value, numberInIteration, callback);
        callback.expandTemplate(modifiedTemplate, predefinedValues);
      }
    }
    else {
      // for CSS
      callback.expandTemplate(token.getKey(), null);
    }
  }

  private static void setAttributeValues(XmlTag tag, List<Pair<String, String>> attr2value, int numberInIteration) {
    for (Iterator<Pair<String, String>> iterator = attr2value.iterator(); iterator.hasNext();) {
      Pair<String, String> pair = iterator.next();
      if (tag.getAttribute(pair.first) != null) {
        tag.setAttribute(pair.first, ZenCodingUtil.getValue(pair, numberInIteration));
        iterator.remove();
      }
    }
  }

  @Nullable
  private static String filterXml(XmlTag tag, CustomTemplateCallback callback, String filterSuffix) {
    PsiElement context = callback.getContext();
    for (ZenCodingFilter filter : ZenCodingFilter.EP_NAME.getExtensions()) {
      if ((filterSuffix == null && filter.isDefaultFilter()) || (filterSuffix != null && filterSuffix.equals(filter.getSuffix()))) {
        if (filter instanceof XmlZenCodingFilter && filter.isDefaultFilter() && filter.isMyContext(context)) {
          return ((XmlZenCodingFilter)filter).toString(tag, context);
        }
      }
    }
    return new XmlZenCodingFilterImpl().toString(tag, context);
  }

  private static void fail() {
    LOG.error("Input string was checked incorrectly during isApplicable() invokation");
  }
}
