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
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.IntArrayList;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
class XmlZenCodingInterpreter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.zencoding.XmlZenCodingInterpreter");
  private static final String ATTRS = "ATTRS";
  private static final String NUMBER_IN_ITERATION_PLACE_HOLDER = "$";

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
    if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_END_TAG_START) {
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
      myCallback.fixEndOffset();
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
    final int n = myTokens.size();
    TemplateToken templateToken = null;
    int number = -1;
    for (int i = startIndex; i < n; i++) {
      Token token = myTokens.get(i);
      switch (myState) {
        case OPERATION:
          if (templateToken != null) {
            if (token instanceof MarkerToken || token instanceof OperationToken) {
              final char sign = token instanceof OperationToken ? ((OperationToken)token).mySign : XmlZenCodingTemplate.MARKER;
              if (sign == '+' || (mySurroundedText == null && sign == XmlZenCodingTemplate.MARKER)) {
                final Object key = new Object();
                myCallback.fixStartOfTemplate(key);
                invokeTemplate(templateToken, myCallback, 0);
                myState = State.WORD;
                if (myCallback.getOffset() != myCallback.getEndOfTemplate(key)) {
                  myCallback.fixEndOffset();
                }
                if (sign == '+') {
                  myCallback.gotoEndOfTemplate(key);
                }
                templateToken = null;
              }
              else if (sign == '>' || (mySurroundedText != null && sign == XmlZenCodingTemplate.MARKER)) {
                startTemplateAndGotoChild(templateToken);
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
            number = ((NumberToken)token).myNumber;
            myState = State.AFTER_NUMBER;
          }
          else {
            fail();
          }
          break;
        case AFTER_NUMBER:
          if (token instanceof MarkerToken || token instanceof OperationToken) {
            char sign = token instanceof OperationToken ? ((OperationToken)token).mySign : XmlZenCodingTemplate.MARKER;
            if (sign == '+' || (mySurroundedText == null && sign == XmlZenCodingTemplate.MARKER)) {
              invokeTemplateSeveralTimes(templateToken, 0, number);
              templateToken = null;
            }
            else if (number > 1) {
              invokeTemplateAndProcessTail(templateToken, 0, number, i + 1);
              return;
            }
            else {
              assert number == 1;
              startTemplateAndGotoChild(templateToken);
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

  private void startTemplateAndGotoChild(TemplateToken templateToken) {
    final Object key = new Object();
    myCallback.fixStartOfTemplate(key);
    invokeTemplate(templateToken, myCallback, 0);
    myState = State.WORD;
    gotoChild(key);
  }

  private void invokeTemplateSeveralTimes(final TemplateToken templateToken,
                                          final int startIndex,
                                          final int count) {
    final Object key = new Object();
    myCallback.fixStartOfTemplate(key);
    for (int i = startIndex; i < count; i++) {
      invokeTemplate(templateToken, myCallback, i);
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
                                            final int tailStart) {
    final Object key = new Object();
    myCallback.fixStartOfTemplate(key);
    for (int i = startIndex; i < count; i++) {
      invokeTemplate(templateToken, myCallback, i);
      gotoChild(key);
      interpret(myTokens, tailStart, myCallback, State.WORD, mySurroundedText);
      if (myCallback.getOffset() != myCallback.getEndOfTemplate(key)) {
        myCallback.fixEndOffset();
      }
      myCallback.gotoEndOfTemplate(key);
    }
    finish();
  }

  private static boolean containsAttrsVar(TemplateImpl template) {
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
  private static Map<String, String> buildPredefinedValues(List<Pair<String, String>> attribute2value, int numberInIteration) {
    StringBuilder result = new StringBuilder();
    for (Iterator<Pair<String, String>> it = attribute2value.iterator(); it.hasNext();) {
      Pair<String, String> pair = it.next();
      String name = pair.first;
      String value = getValue(pair, numberInIteration);
      result.append(name).append("=\"").append(value).append('"');
      if (it.hasNext()) {
        result.append(' ');
      }
    }
    String attributes = result.toString();
    attributes = attributes.length() > 0 ? ' ' + attributes : null;
    Map<String, String> predefinedValues = null;
    if (attributes != null) {
      predefinedValues = new HashMap<String, String>();
      predefinedValues.put(ATTRS, attributes);
    }
    return predefinedValues;
  }

  private static String getValue(Pair<String, String> pair, int numberInIteration) {
    return pair.second.replace(NUMBER_IN_ITERATION_PLACE_HOLDER, Integer.toString(numberInIteration + 1));
  }

  @Nullable
  private static String addAttrsVar(TemplateImpl modifiedTemplate, XmlTag tag) {
    String text = tag.getContainingFile().getText();
    PsiElement[] children = tag.getChildren();
    if (children.length >= 1 &&
        children[0] instanceof XmlToken &&
        ((XmlToken)children[0]).getTokenType() == XmlTokenType.XML_START_TAG_START) {
      PsiElement beforeAttrs = children[0];
      if (children.length >= 2 && children[1] instanceof XmlToken && ((XmlToken)children[1]).getTokenType() == XmlTokenType.XML_NAME) {
        beforeAttrs = children[1];
      }
      TextRange range = beforeAttrs.getTextRange();
      if (range == null) {
        return null;
      }
      int offset = range.getEndOffset();
      text = text.substring(0, offset) + " $ATTRS$" + text.substring(offset);
      modifiedTemplate.addVariable(ATTRS, "", "", false);
      return text;
    }
    return null;
  }

  private static void invokeTemplate(TemplateToken token,
                                     final CustomTemplateCallback callback,
                                     int numberInIteration) {
    List<Pair<String, String>> attr2value = new ArrayList<Pair<String, String>>(token.myAttribute2Value);
    if (callback.isLiveTemplateApplicable(token.myKey)) {
      invokeExistingLiveTemplate(token, callback, numberInIteration, attr2value);
    }
    else {
      TemplateImpl template = new TemplateImpl("", "");
      template.addTextSegment('<' + token.myKey);
      if (attr2value.size() > 0) {
        template.addVariable(ATTRS, "", "", false);
        template.addVariableSegment(ATTRS);
      }
      template.addTextSegment(">");
      if (XmlZenCodingTemplate.isTrueXml(callback) || !HtmlUtil.isSingleHtmlTag(token.myKey)) {
        template.addVariableSegment(TemplateImpl.END);
        template.addTextSegment("</" + token.myKey + ">");
      }
      template.setToReformat(true);
      Map<String, String> predefinedValues = buildPredefinedValues(attr2value, numberInIteration);
      callback.expandTemplate(template, predefinedValues);
    }
  }

  private static void invokeExistingLiveTemplate(TemplateToken token,
                                                 CustomTemplateCallback callback,
                                                 int numberInIteration,
                                                 List<Pair<String, String>> attr2value) {
    if (token.myTemplate != null) {
      if (attr2value.size() > 0 || XmlZenCodingTemplate.isTrueXml(callback)) {
        TemplateImpl modifiedTemplate = token.myTemplate.copy();
        XmlTag tag = XmlZenCodingTemplate.parseXmlTagInTemplate(token.myTemplate.getString(), callback, true);
        if (tag != null) {
          for (Iterator<Pair<String, String>> iterator = attr2value.iterator(); iterator.hasNext();) {
            Pair<String, String> pair = iterator.next();
            if (tag.getAttribute(pair.first) != null) {
              tag.setAttribute(pair.first, getValue(pair, numberInIteration));
              iterator.remove();
            }
          }
          if (XmlZenCodingTemplate.isTrueXml(callback)) {
            closeUnclosingTags(tag);
          }
          String text = null;
          if (!containsAttrsVar(modifiedTemplate) && attr2value.size() > 0) {
            String textWithAttrs = addAttrsVar(modifiedTemplate, tag);
            if (textWithAttrs != null) {
              text = textWithAttrs;
            }
            else {
              for (Iterator<Pair<String, String>> iterator = attr2value.iterator(); iterator.hasNext();) {
                Pair<String, String> pair = iterator.next();
                tag.setAttribute(pair.first, getValue(pair, numberInIteration));
                iterator.remove();
              }
            }
          }
          if (text == null) {
            text = tag.getContainingFile().getText();
          }
          modifiedTemplate.setString(text);
          removeVariablesWhichHasNoSegment(modifiedTemplate);
          Map<String, String> predefinedValues = buildPredefinedValues(attr2value, numberInIteration);
          callback.expandTemplate(modifiedTemplate, predefinedValues);
          return;
        }
      }
      callback.expandTemplate(token.myTemplate, null);
    }
    else {
      Map<String, String> predefinedValues = buildPredefinedValues(attr2value, numberInIteration);
      callback.expandTemplate(token.myKey, predefinedValues);
    }
  }

  private static boolean isTagClosed(@NotNull XmlTag tag) {
    ASTNode node = tag.getNode();
    assert node != null;
    final ASTNode emptyTagEnd = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(node);
    final ASTNode endTagEnd = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(node);
    return emptyTagEnd != null || endTagEnd != null;
  }

  @SuppressWarnings({"ConstantConditions"})
  private static void closeUnclosingTags(@NotNull XmlTag root) {
    final List<SmartPsiElementPointer<XmlTag>> tagToClose = new ArrayList<SmartPsiElementPointer<XmlTag>>();
    Project project = root.getProject();
    final SmartPointerManager manager = SmartPointerManager.getInstance(project);
    root.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlTag(final XmlTag tag) {
        if (!isTagClosed(tag)) {
          tagToClose.add(manager.createLazyPointer(tag));
        }
      }
    });
    for (final SmartPsiElementPointer<XmlTag> pointer : tagToClose) {
      final XmlTag tag = pointer.getElement();
      if (tag != null) {
        final ASTNode child = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());
        if (child != null) {
          final int offset = child.getTextRange().getStartOffset();
          VirtualFile file = tag.getContainingFile().getVirtualFile();
          if (file != null) {
            final Document document = FileDocumentManager.getInstance().getDocument(file);
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                document.replaceString(offset, tag.getTextRange().getEndOffset(), "/>");
              }
            });
          }
        }
      }
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
  }

  private static void fail() {
    LOG.error("Input string was checked incorrectly during isApplicable() invokation");
  }
}
