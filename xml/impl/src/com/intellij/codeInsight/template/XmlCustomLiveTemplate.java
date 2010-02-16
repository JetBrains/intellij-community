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
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlCustomLiveTemplate implements CustomLiveTemplate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.XmlCustomLiveTemplate");

  private static final String ATTRS = "ATTRS";

  private static final String OPERATIONS = ">+*";
  private static final String SELECTORS = ".#[";
  private static final char MARKER = '$';
  private static final String ID = "id";
  private static final String CLASS = "class";

  private static enum MyState {
    OPERATION, WORD, AFTER_NUMBER, NUMBER
  }

  private static class MyToken {
  }

  private static class MyMarkerToken extends MyToken {
  }

  private static class MyTemplateToken extends MyToken {
    final String myKey;
    final Map<String, String> myAttribute2Value;

    MyTemplateToken(String key, Map<String, String> attribute2value) {
      myKey = key;
      myAttribute2Value = attribute2value;
    }
  }

  private static class MyNumberToken extends MyToken {
    final int myNumber;

    MyNumberToken(int number) {
      myNumber = number;
    }
  }

  private static class MyOperationToken extends MyToken {
    final char mySign;

    MyOperationToken(char sign) {
      mySign = sign;
    }
  }

  private static boolean isTemplateKeyPart(char c) {
    return !Character.isWhitespace(c) && OPERATIONS.indexOf(c) < 0;
  }

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
  private static MyTemplateToken parseSelectors(@NotNull String text) {
    String templateKey = null;
    Map<String, String> attribute2value = new HashMap<String, String>();
    final List<String> classes = new ArrayList<String>();
    StringBuilder builder = new StringBuilder();
    char lastDelim = 0;
    text += MARKER;
    for (int i = 0, n = text.length(); i < n; i++) {
      char c = text.charAt(i);
      if (c == '#' || c == '.' || c == '[' || c == ']' || i == n - 1) {
        if (c != ']') {
          switch (lastDelim) {
            case 0:
              templateKey = builder.toString();
              break;
            case '#':
              attribute2value.put(ID, builder.toString());
              break;
            case '.':
              if (builder.length() > 0) {
                classes.add(builder.toString());
              }
              else {
                return null;
              }
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
          if (pair == null || attribute2value.containsKey(pair.first)) {
            return null;
          }
          attribute2value.put(pair.first, pair.second);
        }
        lastDelim = c;
        builder = new StringBuilder();
      }
      else {
        builder.append(c);
      }
    }
    if (classes.size() > 0) {
      if (attribute2value.containsKey(CLASS)) {
        return null;
      }
      StringBuilder classesAttrValue = new StringBuilder();
      for (int i = 0; i < classes.size(); i++) {
        classesAttrValue.append(classes.get(i));
        if (i < classes.size() - 1) {
          classesAttrValue.append(' ');
        }
      }
      attribute2value.put(CLASS, classesAttrValue.toString());
    }
    return new MyTemplateToken(templateKey, attribute2value);
  }

  @Nullable
  private static List<MyToken> parse(@NotNull String text, @NotNull CustomTemplateCallback callback) {
    text += MARKER;
    StringBuilder templateKeyBuilder = new StringBuilder();
    List<MyToken> result = new ArrayList<MyToken>();
    for (int i = 0, n = text.length(); i < n; i++) {
      char c = text.charAt(i);
      if (i == n - 1 || OPERATIONS.indexOf(c) >= 0) {
        String key = templateKeyBuilder.toString();
        templateKeyBuilder = new StringBuilder();
        int num = parseNonNegativeInt(key);
        if (num > 0) {
          result.add(new MyNumberToken(num));
        }
        else {
          if (key.length() == 0) {
            return null;
          }
          String prefix = getPrefix(key);
          if (!callback.isLiveTemplateApplicable(prefix) && prefix.indexOf('<') >= 0) {
            return null;
          }
          MyTemplateToken token = parseSelectors(key);
          if (token == null) {
            return null;
          }
          result.add(token);
        }
        result.add(i < n - 1 ? new MyOperationToken(c) : new MyMarkerToken());
      }
      else if (isTemplateKeyPart(c)) {
        templateKeyBuilder.append(c);
      }
      else {
        return null;
      }
    }
    return result;
  }

  private static boolean check(@NotNull Collection<MyToken> tokens) {
    MyState state = MyState.WORD;
    for (MyToken token : tokens) {
      if (token instanceof MyMarkerToken) {
        break;
      }
      switch (state) {
        case OPERATION:
          if (token instanceof MyOperationToken) {
            state = ((MyOperationToken)token).mySign == '*' ? MyState.NUMBER : MyState.WORD;
          }
          else {
            return false;
          }
          break;
        case WORD:
          if (token instanceof MyTemplateToken) {
            state = MyState.OPERATION;
          }
          else {
            return false;
          }
          break;
        case NUMBER:
          if (token instanceof MyNumberToken) {
            state = MyState.AFTER_NUMBER;
          }
          else {
            return false;
          }
          break;
        case AFTER_NUMBER:
          if (token instanceof MyOperationToken && ((MyOperationToken)token).mySign != '*') {
            state = MyState.WORD;
          }
          else {
            return false;
          }
          break;
      }
    }
    return state == MyState.OPERATION || state == MyState.AFTER_NUMBER;
  }

  public boolean isApplicable(@NotNull String key, @NotNull CustomTemplateCallback callback) {
    if (callback.getFile() instanceof XmlFile) {
      List<MyToken> tokens = parse(key, callback);
      if (tokens != null) {
        return check(tokens);
      }
    }
    return false;
  }

  public void execute(@NotNull String key, @NotNull CustomTemplateCallback callback, @Nullable TemplateInvokationListener listener) {
    List<MyToken> tokens = parse(key, callback);
    assert tokens != null;
    MyInterpreter interpreter = new MyInterpreter(tokens, callback, MyState.WORD, listener);
    interpreter.invoke(0);
  }

  private static void fail() {
    LOG.error("Input string was checked incorrectly during isApplicable() invokation");
  }

  @NotNull
  private static String buildAttributesString(Map<String, String> attribute2value) {
    StringBuilder result = new StringBuilder();
    Set<Map.Entry<String, String>> entries = attribute2value.entrySet();
    for (Iterator<Map.Entry<String, String>> it = entries.iterator(); it.hasNext();) {
      Map.Entry<String, String> entry = it.next();
      String name = entry.getKey();
      String value = entry.getValue();
      result.append(name).append("=\"").append(value).append('"');
      if (it.hasNext()) {
        result.append(' ');
      }
    }
    return result.toString();
  }

  private static boolean invokeTemplate(MyTemplateToken token,
                                        final CustomTemplateCallback callback,
                                        final TemplateInvokationListener listener) {
    String attributes = buildAttributesString(token.myAttribute2Value);
    attributes = attributes.length() > 0 ? ' ' + attributes : null;
    Map<String, String> predefinedValues = null;
    if (attributes != null) {
      predefinedValues = new HashMap<String, String>();
      predefinedValues.put(ATTRS, attributes);
    }
    if (callback.isLiveTemplateApplicable(token.myKey)) {
      if (attributes != null && !callback.templateContainsVars(token.myKey, ATTRS)) {
        TemplateImpl newTemplate = generateTemplateWithAttributes(token.myKey, attributes, callback);
        if (newTemplate != null) {
          return callback.startTemplate(newTemplate, predefinedValues, listener);
        }
      }
      return callback.startTemplate(token.myKey, predefinedValues, listener);
    }
    else {
      TemplateImpl template = new TemplateImpl("", "");
      template.addTextSegment('<' + token.myKey);
      if (attributes != null) {
        template.addVariable(ATTRS, "", "", false);
        template.addVariableSegment(ATTRS);
      }
      template.addTextSegment(">");
      template.addVariableSegment(TemplateImpl.END);
      template.addTextSegment("</" + token.myKey + ">");
      template.setToReformat(true);
      return callback.startTemplate(template, predefinedValues, listener);
    }
  }

  private static int findPlaceToInsertAttrs(@NotNull TemplateImpl template) {
    String s = template.getString();
    if (s.length() > 0) {
      if (s.charAt(0) != '<') {
        return -1;
      }
      int i = 1;
      while (i < s.length() && !Character.isWhitespace(s.charAt(i)) && s.charAt(i) != '>') {
        i++;
      }
      if (i == 1) {
        return -1;
      }
      if (s.indexOf('>', i) >= i) {
        return i;
      }
    }
    return -1;
  }

  @Nullable
  private static TemplateImpl generateTemplateWithAttributes(String key, String attributes, CustomTemplateCallback callback) {
    TemplateImpl template = callback.findApplicableTemplate(key);
    assert template != null;
    String templateString = template.getString();
    int offset = findPlaceToInsertAttrs(template);
    if (offset >= 0) {
      String newTemplateString = templateString.substring(0, offset) + attributes + templateString.substring(offset);
      TemplateImpl newTemplate = template.copy();
      newTemplate.setString(newTemplateString);
      return newTemplate;
    }
    return null;
  }

  private static boolean hasClosingTag(CharSequence text, CharSequence tagName, int offset, int rightBound) {
    if (offset + 1 < text.length() && text.charAt(offset) == '<' && text.charAt(offset + 1) == '/') {
      CharSequence closingTagName = parseTagName(text, offset + 2, rightBound);
      if (tagName.equals(closingTagName)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static CharSequence getPrecedingTagName(CharSequence text, int index, int leftBound) {
    int j = index - 1;
    while (j >= leftBound && Character.isWhitespace(text.charAt(j))) {
      j--;
    }
    if (j < leftBound || text.charAt(j) != '>') {
      return null;
    }
    while (j >= leftBound && text.charAt(j) != '<') {
      j--;
    }
    if (j < 0) {
      return null;
    }
    return parseTagName(text, j + 1, index);
  }

  @Nullable
  private static CharSequence parseTagName(CharSequence text, int index, int rightBound) {
    int j = index;
    if (rightBound > text.length()) {
      rightBound = text.length();
    }
    while (j < rightBound && !Character.isWhitespace(text.charAt(j)) && text.charAt(j) != '>') {
      j++;
    }
    if (j >= text.length()) {
      return null;
    }
    return text.subSequence(index, j);
  }

  private class MyInterpreter {
    private final List<MyToken> myTokens;
    private final CustomTemplateCallback myCallback;
    private final TemplateInvokationListener myListener;
    private MyState myState;
    private int myEndOffset = -1;

    private MyInterpreter(List<MyToken> tokens,
                          CustomTemplateCallback callback,
                          MyState initialState,
                          TemplateInvokationListener listener) {
      myTokens = tokens;
      myCallback = callback;
      myListener = listener;
      myState = initialState;
    }

    private void fixEndOffset() {
      if (myEndOffset < 0) {
        myEndOffset = myCallback.getOffset();
      }
    }

    private void finish(boolean inSeparateEvent) {
      Editor editor = myCallback.getEditor();
      if (myEndOffset >= 0) {
        editor.getCaretModel().moveToOffset(myEndOffset);
      }
      editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      if (myListener != null) {
        myListener.finished(inSeparateEvent);
      }
    }

    private void gotoChild(Object templateBoundsKey) {
      int startOfTemplate = myCallback.getStartOfTemplate(templateBoundsKey);
      int endOfTemplate = myCallback.getEndOfTemplate(templateBoundsKey);
      Editor editor = myCallback.getEditor();
      int offset = myCallback.getOffset();
      Document document = myCallback.getEditor().getDocument();
      CharSequence text = document.getCharsSequence();
      CharSequence tagName = getPrecedingTagName(text, offset, startOfTemplate);
      if (tagName != null) {
        if (!hasClosingTag(text, tagName, offset, endOfTemplate)) {
          document.insertString(offset, "</" + tagName + '>');
        }
      }
      else if (offset != endOfTemplate) {
        tagName = getPrecedingTagName(text, endOfTemplate, startOfTemplate);
        if (tagName != null) {
          fixEndOffset();
          document.insertString(endOfTemplate, "</" + tagName + '>');
          editor.getCaretModel().moveToOffset(endOfTemplate);
        }
      }
    }

    public boolean invoke(int startIndex) {
      final int n = myTokens.size();
      MyTemplateToken templateToken = null;
      int number = -1;
      for (int i = startIndex; i < n; i++) {
        final int finalI = i;
        MyToken token = myTokens.get(i);
        switch (myState) {
          case OPERATION:
            if (templateToken != null) {
              if (token instanceof MyMarkerToken || token instanceof MyOperationToken) {
                final char sign = token instanceof MyOperationToken ? ((MyOperationToken)token).mySign : MARKER;
                if (sign == MARKER || sign == '+') {
                  final Object key = new Object();
                  myCallback.fixStartOfTemplate(key);
                  TemplateInvokationListener listener = new TemplateInvokationListener() {
                    public void finished(boolean inSeparateEvent) {
                      myState = MyState.WORD;
                      fixEndOffset();
                      if (sign == '+') {
                        myCallback.gotoEndOfTemplate(key);
                      }
                      if (inSeparateEvent) {
                        invoke(finalI + 1);
                      }
                    }
                  };
                  if (!invokeTemplate(templateToken, myCallback, listener)) {
                    return false;
                  }
                  templateToken = null;
                }
                else if (sign == '>') {
                  if (!startTemplateAndGotoChild(templateToken, finalI)) {
                    return false;
                  }
                  templateToken = null;
                }
                else if (sign == '*') {
                  myState = MyState.NUMBER;
                }
              }
              else {
                fail();
              }
            }
            break;
          case WORD:
            if (token instanceof MyTemplateToken) {
              templateToken = ((MyTemplateToken)token);
              myState = MyState.OPERATION;
            }
            else {
              fail();
            }
            break;
          case NUMBER:
            if (token instanceof MyNumberToken) {
              number = ((MyNumberToken)token).myNumber;
              myState = MyState.AFTER_NUMBER;
            }
            else {
              fail();
            }
            break;
          case AFTER_NUMBER:
            if (token instanceof MyMarkerToken || token instanceof MyOperationToken) {
              char sign = token instanceof MyOperationToken ? ((MyOperationToken)token).mySign : MARKER;
              if (sign == MARKER || sign == '+') {
                if (!invokeTemplateSeveralTimes(templateToken, number, finalI)) {
                  return false;
                }
                templateToken = null;
              }
              else if (number > 1) {
                return invokeTemplateAndProcessTail(templateToken, i + 1, number);
              }
              else {
                assert number == 1;
                if (!startTemplateAndGotoChild(templateToken, finalI)) {
                  return false;
                }
                templateToken = null;
              }
              myState = MyState.WORD;
            }
            else {
              fail();
            }
            break;
        }
      }
      finish(startIndex == n);
      return true;
    }

    private boolean startTemplateAndGotoChild(MyTemplateToken templateToken, final int index) {
      final Object key = new Object();
      myCallback.fixStartOfTemplate(key);
      TemplateInvokationListener listener = new TemplateInvokationListener() {
        public void finished(boolean inSeparateEvent) {
          myState = MyState.WORD;
          gotoChild(key);
          if (inSeparateEvent) {
            invoke(index + 1);
          }
        }
      };
      if (!invokeTemplate(templateToken, myCallback, listener)) {
        return false;
      }
      return true;
    }

    private boolean invokeTemplateSeveralTimes(final MyTemplateToken templateToken, final int count, final int index) {
      final Object key = new Object();
      myCallback.fixStartOfTemplate(key);
      for (int i = 0; i < count; i++) {
        final int finalI = i;
        TemplateInvokationListener listener = new TemplateInvokationListener() {
          public void finished(boolean inSeparateEvent) {
            myState = MyState.WORD;
            fixEndOffset();
            myCallback.gotoEndOfTemplate(key);
            if (inSeparateEvent) {
              int newCount = count - finalI - 1;
              if (newCount > 0) {
                invokeTemplateSeveralTimes(templateToken, newCount, index);
              }
              else {
                invoke(index + 1);
              }
            }
          }
        };
        if (!invokeTemplate(templateToken, myCallback, listener)) {
          return false;
        }
      }
      return true;
    }

    private boolean invokeTemplateAndProcessTail(final MyTemplateToken templateToken, final int tailStart, final int count) {
      final Object key = new Object();
      myCallback.fixStartOfTemplate(key);
      for (int i = 0; i < count; i++) {
        final int finalI = i;
        final boolean[] flag = new boolean[]{false};
        TemplateInvokationListener listener = new TemplateInvokationListener() {
          public void finished(boolean inSeparateEvent) {
            gotoChild(key);
            MyInterpreter interpreter = new MyInterpreter(myTokens, myCallback, MyState.WORD, new TemplateInvokationListener() {
              public void finished(boolean inSeparateEvent) {
                fixEndOffset();
                myCallback.gotoEndOfTemplate(key);
                if (inSeparateEvent) {
                  invokeTemplateAndProcessTail(templateToken, tailStart, count - finalI - 1);
                }
              }
            });
            if (interpreter.invoke(tailStart)) {
              if (inSeparateEvent) {
                invokeTemplateAndProcessTail(templateToken, tailStart, count - finalI - 1);
              }
            }
            else {
              flag[0] = true;
            }
          }
        };
        if (!invokeTemplate(templateToken, myCallback, listener) || flag[0]) {
          return false;
        }
      }
      finish(count == 0);
      return true;
    }
  }
}