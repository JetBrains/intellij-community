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
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlCustomLiveTemplate implements CustomLiveTemplate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.XmlCustomLiveTemplate");

  private static final String ATTRS = "ATTRS";

  private static final String POSSIBLE_OPERATIONS = ">+*";
  private static final String HTML_SELECTORS = ".#";
  private static final char MARKER = '$';

  private static enum MyState {
    OPERATION, WORD, AFTER_NUMBER, NUMBER
  }

  private static class MyToken {
  }

  private static class MyMarkerToken extends MyToken {
  }

  private static class MyTemplateToken extends MyToken {
    final String myKey;

    MyTemplateToken(String key) {
      myKey = key;
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
    return !Character.isWhitespace(c) && POSSIBLE_OPERATIONS.indexOf(c) < 0;
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
      if (HTML_SELECTORS.indexOf(c) >= 0) {
        return templateKey.substring(0, i);
      }
    }
    return templateKey;
  }

  @Nullable
  private static List<MyToken> parse(@NotNull String text, @NotNull CustomTemplateCallback callback) {
    text += MARKER;
    StringBuilder templateKeyBuilder = new StringBuilder();
    List<MyToken> result = new ArrayList<MyToken>();
    for (int i = 0, n = text.length(); i < n; i++) {
      char c = text.charAt(i);
      if (i == n - 1 || POSSIBLE_OPERATIONS.indexOf(c) >= 0) {
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
          if (callback.isLiveTemplateApplicable(prefix)) {
            if (!prefix.equals(key) && !callback.isTemplateContainsVars(prefix, ATTRS)) {
              return null;
            }
          }
          else if (prefix.indexOf('<') >= 0) {
            return null;
          }
          result.add(new MyTemplateToken(key));
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
  private static String buildAttributesString(@Nullable String id, @NotNull List<String> classes) {
    StringBuilder result = new StringBuilder();
    if (id != null) {
      result.append("id=\"").append(id).append('"');
      if (classes.size() > 0) {
        result.append(' ');
      }
    }
    if (classes.size() > 0) {
      result.append("class=\"");
      for (int i = 0; i < classes.size(); i++) {
        result.append(classes.get(i));
        if (i < classes.size() - 1) {
          result.append(' ');
        }
      }
      result.append('"');
    }
    return result.toString();
  }

  private static boolean invokeTemplate(String key, final CustomTemplateCallback callback, final TemplateInvokationListener listener) {
    if (callback.getFile().getFileType() instanceof HtmlFileType) {
      String templateKey = null;
      String id = null;
      final List<String> classes = new ArrayList<String>();
      StringBuilder builder = new StringBuilder();
      char lastDelim = 0;
      key += MARKER;
      for (int i = 0, n = key.length(); i < n; i++) {
        char c = key.charAt(i);
        if (c == '#' || c == '.' || i == n - 1) {
          switch (lastDelim) {
            case 0:
              templateKey = builder.toString();
              break;
            case '#':
              id = builder.toString();
              break;
            case '.':
              if (builder.length() > 0) {
                classes.add(builder.toString());
              }
              break;
          }
          lastDelim = c;
          builder = new StringBuilder();
        }
        else {
          builder.append(c);
        }
      }
      String attributes = buildAttributesString(id, classes);
      return startTemplate(templateKey, callback, listener, attributes.length() > 0 ? ' ' + attributes : null);
    }
    return startTemplate(key, callback, listener, null);
  }

  private static boolean startTemplate(String key,
                                       CustomTemplateCallback callback,
                                       TemplateInvokationListener listener,
                                       @Nullable String attributes) {
    Map<String, String> predefinedValues = null;
    if (attributes != null) {
      predefinedValues = new HashMap<String, String>();
      predefinedValues.put(ATTRS, attributes);
    }
    if (callback.isLiveTemplateApplicable(key)) {
      return callback.startTemplate(key, predefinedValues, listener);
    }
    else {
      TemplateImpl template = new TemplateImpl("", "");
      template.addTextSegment('<' + key);
      if (attributes != null) {
        template.addVariable(ATTRS, "", "", false);
        template.addVariableSegment(ATTRS);
      }
      template.addTextSegment(">");
      template.addVariableSegment(TemplateImpl.END);
      template.addTextSegment("</" + key + ">");
      template.setToReformat(true);
      return callback.startTemplate(template, predefinedValues, listener);
    }
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
        myEndOffset = getOffset();
      }
    }

    private int getOffset() {
      return myCallback.getEditor().getCaretModel().getOffset();
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

    public boolean invoke(int startIndex) {
      final int n = myTokens.size();
      String templateKey = null;
      int number = -1;
      for (int i = startIndex; i < n; i++) {
        final int finalI = i;
        MyToken token = myTokens.get(i);
        switch (myState) {
          case OPERATION:
            if (templateKey != null) {
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
                  if (!invokeTemplate(templateKey, myCallback, listener)) {
                    return false;
                  }
                  templateKey = null;
                }
                else if (sign == '>') {
                  if (!startTemplate(templateKey, finalI)) {
                    return false;
                  }
                  templateKey = null;
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
              templateKey = ((MyTemplateToken)token).myKey;
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
                if (!invokeTemplateSeveralTimes(templateKey, number, finalI)) {
                  return false;
                }
                templateKey = null;
              }
              else if (number > 1) {
                return invokeTemplateAndProcessTail(templateKey, i + 1, number);
              }
              else {
                assert number == 1;
                if (!startTemplate(templateKey, finalI)) {
                  return false;
                }
                templateKey = null;
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

    private boolean startTemplate(String templateKey, final int index) {
      TemplateInvokationListener listener = new TemplateInvokationListener() {
        public void finished(boolean inSeparateEvent) {
          myState = MyState.WORD;
          if (inSeparateEvent) {
            invoke(index + 1);
          }
        }
      };
      if (!invokeTemplate(templateKey, myCallback, listener)) {
        return false;
      }
      return true;
    }

    private boolean invokeTemplateSeveralTimes(final String templateKey, final int count, final int index) {
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
                invokeTemplateSeveralTimes(templateKey, newCount, index);
              }
              else {
                invoke(index + 1);
              }
            }
          }
        };
        if (!invokeTemplate(templateKey, myCallback, listener)) {
          return false;
        }
      }
      return true;
    }

    private boolean invokeTemplateAndProcessTail(final String templateKey, final int tailStart, final int count) {
      final Object key = new Object();
      myCallback.fixStartOfTemplate(key);
      for (int i = 0; i < count; i++) {
        final boolean[] flag = new boolean[]{false};
        final int finalI = i;
        TemplateInvokationListener listener = new TemplateInvokationListener() {
          public void finished(boolean inSeparateEvent) {
            MyInterpreter interpreter = new MyInterpreter(myTokens, myCallback, MyState.WORD, new TemplateInvokationListener() {
              public void finished(boolean inSeparateEvent) {
                fixEndOffset();
                myCallback.gotoEndOfTemplate(key);
                if (inSeparateEvent) {
                  invokeTemplateAndProcessTail(templateKey, tailStart, count - finalI - 1);
                }
              }
            });
            if (interpreter.invoke(tailStart)) {
              if (inSeparateEvent) {
                invokeTemplateAndProcessTail(templateKey, tailStart, count - finalI - 1);
              }
            }
            else {
              flag[0] = true;
            }
          }
        };
        if (!invokeTemplate(templateKey, myCallback, listener) || flag[0]) {
          return false;
        }
      }
      finish(count == 0);
      return true;
    }
  }
}