package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.Result;

import java.awt.*;

public class EditorUtil {
  private EditorUtil() { }

  public static int getLastVisualLineColumnNumber(Editor editor, int line) {
    VisualPosition visStart = new VisualPosition(line, 0);
    LogicalPosition logStart = editor.visualToLogicalPosition(visStart);
    int lastLogLine = logStart.line;
    while (lastLogLine < editor.getDocument().getLineCount() - 1) {
      logStart = new LogicalPosition(logStart.line + 1, logStart.column);
      VisualPosition tryVisible = editor.logicalToVisualPosition(logStart);
      if (tryVisible.line != visStart.line) break;
      lastLogLine = logStart.line;
    }

    int lastLine = editor.getDocument().getLineCount() - 1;
    if (lastLine < 0) {
      return 0;
    }
    return editor.offsetToVisualPosition(editor.getDocument().getLineEndOffset(Math.min(lastLogLine, lastLine))).column;
  }

  public static float calcVerticalScrollProportion(Editor editor) {
    Rectangle viewRect = editor.getScrollingModel().getVisibleAreaOnScrollingFinished();
    if (viewRect.height == 0) {
      return 0;
    }
    LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
    Point location = editor.logicalPositionToXY(pos);
    return (location.y - viewRect.y) / (float) viewRect.height;
  }

  public static void setVerticalScrollProportion(Editor editor, float proportion) {
    Rectangle viewRect = editor.getScrollingModel().getVisibleArea();
    LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
    Point caretLocation = editor.logicalPositionToXY(caretPosition);
    int yPos = caretLocation.y;
    yPos -= viewRect.height * proportion;
    editor.getScrollingModel().scrollVertically(yPos);
  }

  public static void fillVirtualSpaceUntil(final Editor editor, int columnNumber, int lineNumber) {
    final int offset = editor.logicalPositionToOffset(new LogicalPosition(lineNumber, columnNumber));
    final String filler = EditorModificationUtil.calcStringToFillVitualSpace(editor);
    if (filler.length() > 0) {
      new WriteAction(){
        protected void run(final Result result) throws Throwable {
          editor.getDocument().insertString(offset, filler);
        }
      }.execute();
    }
  }

  public static int calcOffset(Editor editor, CharSequence text, int start, int end, int columnNumber, int tabSize) {
    // If all tabs here goes before any other chars in the line we may use an optimization here.
    boolean useOptimization = true;
    boolean hasNonTabs = false;
    boolean hasTabs = false;
    final int maxScanIndex = Math.min(start + columnNumber, end);

    for (int i = start; i < maxScanIndex; i++) {
      if (text.charAt(i) == '\t') {
        hasTabs = true;
        if (hasNonTabs) {
          useOptimization = false;
          break;
        }
      } else {
        hasNonTabs = true;
      }
    }

    if (editor == null || useOptimization) {
      if (!hasTabs) return Math.min(start + columnNumber, end);

      int shift = 0;
      int offset = start;
      for (; offset < end && offset + shift < start + columnNumber; offset++) {
        if (text.charAt(offset) == '\t') {
          shift += getTabLength(offset + shift - start, tabSize) - 1;
        }
      }
      if (offset + shift > start + columnNumber) {
        offset--;
      }

      return offset;
    }

    EditorImpl editorImpl = (EditorImpl) editor;
    int offset = start;
    IterationState state = new IterationState(editorImpl, offset, false);
    int fontType = state.getMergedAttributes().getFontType();
    int column = 0;
    int x = 0;
    int spaceSize = editorImpl.getSpaceWidth(fontType);
    while (column < columnNumber) {
      if (offset >= state.getEndOffset()) {
        state.advance();

        fontType = state.getMergedAttributes().getFontType();
      }

      char c = offset < end ? text.charAt(offset++) : ' ';
      if (c == '\t') {
        int prevX = x;
        x = editorImpl.nextTabStop(x);
        column += (x - prevX) / spaceSize;
      } else {
        x += editorImpl.charWidth(c, fontType);
        column++;
      }
    }

    if (column > columnNumber) offset--;

    return offset;
  }

  private static int getTabLength(int colNumber, int tabSize) {
    if (tabSize <= 0) {
      tabSize = 1;
    }
    return tabSize - colNumber % tabSize;
  }

  public static int calcColumnNumber(Editor editor, CharSequence text, int start, int offset, int tabSize) {
    boolean useOptimization = true;
    boolean hasNonTabs = false;
    for (int i = start; i < offset; i++) {
      if (text.charAt(i) == '\t') {
        if (hasNonTabs) {
          useOptimization = false;
          break;
        }
      } else {
        hasNonTabs = true;
      }
    }

    if (editor == null || useOptimization) {
      int shift = 0;

      for (int i = start; i < offset; i++) {
        char c = text.charAt(i);
        assert c != '\n' && c != '\r';
        if (c == '\t') {
          shift += getTabLength(i + shift - start, tabSize) - 1;
        }
      }
      return offset - start + shift;
    }

    EditorEx editorImpl = (EditorEx) editor;
    return editorImpl.calcColumnNumber(text, start, offset, tabSize);
  }
}


