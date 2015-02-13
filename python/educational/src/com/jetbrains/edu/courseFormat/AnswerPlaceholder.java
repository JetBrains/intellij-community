package com.jetbrains.edu.courseFormat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.editor.Document;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of windows which user should type in
 */

public class AnswerPlaceholder implements Comparable, StudyStateful {

  @Expose private int line = 0;
  @Expose private int start = 0;
  @Expose private String hint = "";
  @SerializedName("possible_answer")
  @Expose private String possibleAnswer = "";
  @Expose private int length = 0;
  private int myIndex = -1;
  private StudyStatus myStatus = StudyStatus.Unchecked;
  private String myTaskText;
  private MyInitialState myInitialState;


  private TaskFile myTaskFile;

  public void init(final TaskFile file, boolean isRestarted) {
    if (!isRestarted) {
      myInitialState = new MyInitialState(line, length, start);
    }
    myTaskFile = file;
  }

  public StudyStatus getStatus() {
    return myStatus;
  }

  public void setStatus(StudyStatus status, StudyStatus oldStatus) {
    myStatus = status;
  }

  public int getIndex() {
    return myIndex;
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

  public int getLine() {
    return line;
  }

  public void setLine(int line) {
    this.line = line;
  }

  public String getHint() {
    return hint;
  }

  public void setHint(@NotNull final String hint) {
    this.hint = hint;
  }

  public String getPossibleAnswer() {
    return possibleAnswer;
  }

  public void setPossibleAnswer(String possibleAnswer) {
    this.possibleAnswer = possibleAnswer;
  }

  public MyInitialState getInitialState() {
    return myInitialState;
  }

  public void setInitialState(@NotNull final MyInitialState initialState) {
    myInitialState = initialState;
  }

  public TaskFile getTaskFile() {
    return myTaskFile;
  }

  public JBColor getColor() {
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

  public boolean isValid(@NotNull final Document document) {
    boolean isLineValid = line < document.getLineCount() && line >= 0;
    if (!isLineValid) return false;
    boolean isStartValid = start >= 0 && start < document.getLineEndOffset(line);
    boolean isLengthValid = (getRealStartOffset(document) + length) <= document.getTextLength();
    return isLengthValid && isStartValid;
  }

  @Override
  public int compareTo(@NotNull Object o) {
    AnswerPlaceholder answerPlaceholder = (AnswerPlaceholder)o;
    if (answerPlaceholder.getTaskFile() != myTaskFile) {
      throw new ClassCastException();
    }
    int lineDiff = line - answerPlaceholder.line;
    if (lineDiff == 0) {
      return start - answerPlaceholder.start;
    }
    return lineDiff;
  }

  /**
   * Returns window to its initial state
   */
  public void reset() {
    myStatus = StudyStatus.Unchecked;
    line = myInitialState.myLine;
    start = myInitialState.myStart;
    length = myInitialState.myLength;
  }

  private static class MyInitialState {
    public int myLine = -1;
    public int myLength = -1;
    public int myStart = -1;

    public MyInitialState() {
    }

    public MyInitialState(int line, int length, int start) {
      myLine = line;
      myLength = length;
      myStart = start;
    }
  }
}
