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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlCustomLiveTemplate implements CustomLiveTemplate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.XmlCustomLiveTemplate");

  private static final String POSSIBLE_OPERATIONS = ">+*";
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
          if (!callback.isLiveTemplateApplicable(key) && key.indexOf('<') >= 0) {
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
    MyInterpreter interpreter = new MyInterpreter(tokens, 0, callback, MyState.WORD, listener);
    interpreter.iter();
  }

  private static void fail() {
    LOG.error("Input string was checked incorrectly during isApplicable() invokation");
  }

  private static boolean invokeTemplate(String key, CustomTemplateCallback callback, TemplateInvokationListener listener) {
    if (callback.isLiveTemplateApplicable(key)) {
      return callback.startTemplate(key, listener);
    }
    else {
      TemplateImpl template = new TemplateImpl("", "");
      template.addTextSegment('<' + key + '>');
      template.addVariableSegment(TemplateImpl.END);
      template.addTextSegment("</" + key + ">");
      return callback.startTemplate(template, listener);
    }
  }

  private class MyInterpreter extends Iteration {
    private final List<MyToken> myTokens;
    private final CustomTemplateCallback myCallback;
    private final TemplateInvokationListener myListener;
    private MyState myState;
    private int myEndOffset = -1;

    private MyInterpreter(List<MyToken> tokens,
                          int startIndex,
                          CustomTemplateCallback callback,
                          MyState initialState,
                          TemplateInvokationListener listener) {
      super(startIndex, tokens.size(), null);
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

    private int getTextLength() {
      return myCallback.getEditor().getDocument().getCharsSequence().length();
    }

    private void moveCaret(int delta) {
      myCallback.getEditor().getCaretModel().moveToOffset(delta);
    }

    private void finish(boolean inSeparateEvent) {
      Editor editor = myCallback.getEditor();
      if (myEndOffset >= 0) {
        editor.getCaretModel().moveToOffset(myEndOffset);
      }
      editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      if (myListener != null) {
        myListener.finished(inSeparateEvent, true);
      }
    }

    @Override
    protected void next() {
      if (myIndex == myMaxIndex - 1) {
        finish(true);
      }
      super.next();
    }

    @Override
    protected void iter() {
      String templateKey = null;
      int number = -1;
      for (; myIndex < myMaxIndex; myIndex++) {
        MyToken token = myTokens.get(myIndex);
        switch (myState) {
          case OPERATION:
            if (templateKey != null) {
              if (token instanceof MyMarkerToken || token instanceof MyOperationToken) {
                final char sign = token instanceof MyOperationToken ? ((MyOperationToken)token).mySign : MARKER;
                if (sign == MARKER || sign == '+') {
                  final int offsetBefore = getOffset();
                  final int lengthBefore = getTextLength();
                  TemplateInvokationListener listener = new TemplateInvokationListener() {
                    public void finished(boolean inSeparateEvent, boolean success) {
                      myState = MyState.WORD;
                      fixEndOffset();
                      if (sign == '+') {
                        moveCaret(offsetBefore + getTextLength() - lengthBefore);
                      }
                      if (inSeparateEvent) {
                        next();
                      }
                    }
                  };
                  if (!invokeTemplate(templateKey, myCallback, listener)) {
                    return;
                  }
                  templateKey = null;
                }
                else if (sign == '>') {
                  if (!startTemplate(templateKey)) {
                    return;
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
                ConsecutiveTemplateInvokation iteration = new ConsecutiveTemplateInvokation(templateKey, number);
                iteration.iter();
                if (!iteration.isFinished()) {
                  return;
                }
                templateKey = null;
              }
              else if (number > 1) {
                ConsecutiveTemplateWithTailInvokation iteration =
                  new ConsecutiveTemplateWithTailInvokation(templateKey, myTokens, myIndex + 1, number);
                iteration.iter();
                if (iteration.isFinished()) {
                  myIndex = myMaxIndex;
                  finish(false);
                }
                return;
              }
              else {
                assert number == 1;
                if (!startTemplate(templateKey)) {
                  return;
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
      finish(false);
    }

    private boolean startTemplate(String templateKey) {
      TemplateInvokationListener listener = new TemplateInvokationListener() {
        public void finished(boolean inSeparateEvent, boolean success) {
          myState = MyState.WORD;
          if (inSeparateEvent) {
            next();
          }
        }
      };
      if (!invokeTemplate(templateKey, myCallback, listener)) {
        return false;
      }
      return true;
    }

    private class ConsecutiveTemplateInvokation extends Iteration {
      private final String myTemplateKey;

      public ConsecutiveTemplateInvokation(String templateKey, int count) {
        super(0, count, MyInterpreter.this);
        myTemplateKey = templateKey;
      }

      @Override
      protected void iter() {
        final int offsetBefore = getOffset();
        final int lengthBefore = getTextLength();
        for (; myIndex < myMaxIndex; myIndex++) {
          TemplateInvokationListener listener = new TemplateInvokationListener() {
            public void finished(boolean inSeparateEvent, boolean success) {
              myState = MyState.WORD;
              fixEndOffset();
              moveCaret(offsetBefore + getTextLength() - lengthBefore);
              if (inSeparateEvent) {
                next();
              }
            }
          };
          if (!invokeTemplate(myTemplateKey, myCallback, listener)) {
            return;
          }
        }
      }
    }

    private class ConsecutiveTemplateWithTailInvokation extends Iteration {
      private final String myTemplateKey;
      private final List<MyToken> myTokens;
      private final int myTailStart;

      public ConsecutiveTemplateWithTailInvokation(String templateKey, List<MyToken> tokens, int tailStart, int count) {
        super(0, count, null);
        assert count > 1;
        assert tailStart < tokens.size();
        myTemplateKey = templateKey;
        myTailStart = tailStart;
        myTokens = tokens;
      }

      @Override
      protected void next() {
        if (myIndex == myMaxIndex - 1) {
          finish(true);
        }
        super.next();
      }

      @Override
      protected void iter() {
        final int offsetBefore = getOffset();
        final int lengthBefore = getTextLength();
        for (; myIndex < myMaxIndex; myIndex++) {
          final boolean[] flag = new boolean[]{false};
          TemplateInvokationListener listener = new TemplateInvokationListener() {
            public void finished(boolean inSeparateEvent, boolean success) {
              MyInterpreter interpreter =
                new MyInterpreter(myTokens, myTailStart, myCallback, MyState.WORD, new TemplateInvokationListener() {
                  public void finished(boolean inSeparateEvent, boolean success) {
                    fixEndOffset();
                    moveCaret(offsetBefore + getTextLength() - lengthBefore);
                    if (inSeparateEvent) {
                      next();
                    }
                  }
                });
              interpreter.iter();
              if (inSeparateEvent && interpreter.isFinished()) {
                next();
              }
              if (!interpreter.isFinished()) {
                flag[0] = true;
              }
            }
          };
          if (!invokeTemplate(myTemplateKey, myCallback, listener) || flag[0]) {
            return;
          }
        }
      }
    }
  }
}
