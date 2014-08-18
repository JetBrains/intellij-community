package com.jetbrains.python.edu.course;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of windows which user should type in
 */


public class TaskWindow implements Comparable, Stateful {

  public int line = 0;
  public int start = 0;
  public String hint = "";
  public String possibleAnswer = "";
  public int length = 0;
  private TaskFile myTaskFile;
  public int myIndex = -1;
  public int myInitialLine = -1;
  public int myInitialStart = -1;
  public int myInitialLength = -1;
  public StudyStatus myStatus = StudyStatus.Unchecked;

  public StudyStatus getStatus() {
    return myStatus;
  }

  public void setStatus(StudyStatus status, StudyStatus oldStatus) {
    myStatus = status;
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public int getLength() {
    return length;
  }

  public void setLength(int length) {
    this.length = length;
  }

  public int getStart() {
    return start;
  }

  public void setStart(int start) {
    this.start = start;
  }

  public void setLine(int line) {
    this.line = line;
  }

  public int getLine() {
    return line;
  }


  /**
   * Draw task window with color according to its status
   */
  public void draw(@NotNull final Editor editor, boolean drawSelection, boolean moveCaret) {
    Document document = editor.getDocument();
    if (!isValid(document)) {
      return;
    }
    TextAttributes defaultTestAttributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.LIVE_TEMPLATE_ATTRIBUTES);
    JBColor color = getColor();
    int startOffset = document.getLineStartOffset(line) + start;
    RangeHighlighter
      rh = editor.getMarkupModel().addRangeHighlighter(startOffset, startOffset + length, HighlighterLayer.LAST + 1,
                                                       new TextAttributes(defaultTestAttributes.getForegroundColor(),
                                                                          defaultTestAttributes.getBackgroundColor(), color,
                                                                          defaultTestAttributes.getEffectType(),
                                                                          defaultTestAttributes.getFontType()),
                                                       HighlighterTargetArea.EXACT_RANGE);
    if (drawSelection) {
      editor.getSelectionModel().setSelection(startOffset, startOffset + length);
    }
    if (moveCaret) {
      editor.getCaretModel().moveToOffset(startOffset);
    }
    rh.setGreedyToLeft(true);
    rh.setGreedyToRight(true);
  }

  public boolean isValid(@NotNull final Document document) {
    boolean isLineValid = line < document.getLineCount() && line >= 0;
    if (!isLineValid) return false;
    boolean isStartValid = start >= 0 && start < document.getLineEndOffset(line);
    boolean isLengthValid = (getRealStartOffset(document) + length) <= document.getTextLength();
    return isLengthValid && isStartValid;
  }

  private JBColor getColor() {
    if (myStatus == StudyStatus.Solved) {
      return JBColor.GREEN;
    }
    if (myStatus == StudyStatus.Failed) {
      return JBColor.RED;
    }
    return JBColor.BLUE;
  }

  public int getRealStartOffset(@NotNull final Document document) {
    return document.getLineStartOffset(line) + start;
  }

  /**
   * Initializes window
   *
   * @param file task file which window belongs to
   */
  public void init(final TaskFile file, boolean isRestarted) {
    if (!isRestarted) {
      myInitialLine = line;
      myInitialLength = length;
      myInitialStart = start;
    }
    myTaskFile = file;
  }

  public TaskFile getTaskFile() {
    return myTaskFile;
  }

  @Override
  public int compareTo(@NotNull Object o) {
    TaskWindow taskWindow = (TaskWindow)o;
    if (taskWindow.getTaskFile() != myTaskFile) {
      throw new ClassCastException();
    }
    int lineDiff = line - taskWindow.line;
    if (lineDiff == 0) {
      return start - taskWindow.start;
    }
    return lineDiff;
  }

  /**
   * Returns window to its initial state
   */
  public void reset() {
    myStatus = StudyStatus.Unchecked;
    line = myInitialLine;
    start = myInitialStart;
    length = myInitialLength;
  }

  public String getHint() {
    return hint;
  }

  public String getPossibleAnswer() {
    return possibleAnswer;
  }

  public void setPossibleAnswer(String possibleAnswer) {
    this.possibleAnswer = possibleAnswer;
  }

  public int getIndex() {
    return myIndex;
  }
}