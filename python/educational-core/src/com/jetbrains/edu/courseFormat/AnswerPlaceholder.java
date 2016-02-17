package com.jetbrains.edu.courseFormat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.editor.Document;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of windows which user should type in
 */

public class AnswerPlaceholder {

  @Expose private int line = 0;
  @Expose private int start = 0;
  @Expose private String hint = "";

  @SerializedName("possible_answer")
  @Expose private String possibleAnswer = "";
  @Expose private int length = 0;
  private int myIndex = -1;
  private String myTaskText;
  private MyInitialState myInitialState;
  private StudyStatus myStatus = StudyStatus.Uninitialized;

  @Transient private TaskFile myTaskFile;

  public void initAnswerPlaceholder(final TaskFile file, boolean isRestarted) {
    if (!isRestarted) {
      setInitialState(new MyInitialState(getLine(), getLength(), getStart()));
      myStatus = file.getTask().getStatus();
    }

    setTaskFile(file);
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

  public void setHint(@Nullable final String hint) {
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

  public String getTaskText() {
    return myTaskText;
  }

  public void setTaskText(String taskText) {
    myTaskText = taskText;
  }

  @Transient
  public TaskFile getTaskFile() {
    return myTaskFile;
  }

  @Transient
  public void setTaskFile(TaskFile taskFile) {
    myTaskFile = taskFile;
  }

  public int getRealStartOffset(@NotNull final Document document) {
    return document.getLineStartOffset(line) + start;
  }

  public int getPossibleAnswerLength() {
    return possibleAnswer.length();
  }

  public boolean isValid(@NotNull final Document document) {
    return isValid(document, length);
  }

  public boolean isValid(@NotNull final Document document, int length) {
    boolean isLineValid = line < document.getLineCount() && line >= 0;
    if (!isLineValid) return false;
    boolean isStartValid = start >= 0 && start < document.getLineEndOffset(line);
    boolean isLengthValid = (getRealStartOffset(document) + length) <= document.getTextLength();
    return isLengthValid && isStartValid;
  }

  /**
   * Returns window to its initial state
   */
  public void reset() {
    line = myInitialState.myLine;
    start = myInitialState.myStart;
    length = myInitialState.myLength;
  }

  public StudyStatus getStatus() {
    return myStatus;
  }

  public void setStatus(StudyStatus status) {
    myStatus = status;
  }

  public static class MyInitialState {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AnswerPlaceholder that = (AnswerPlaceholder)o;

    if (getLine() != that.getLine()) return false;
    if (getStart() != that.getStart()) return false;
    if (getLength() != that.getLength()) return false;
    if (getIndex() != that.getIndex()) return false;
    if (getHint() != null ? !getHint().equals(that.getHint()) : that.getHint() != null) return false;
    if (getPossibleAnswer() != null ? !getPossibleAnswer().equals(that.getPossibleAnswer()) : that.getPossibleAnswer() != null)
      return false;
    if (myTaskText != null ? !myTaskText.equals(that.myTaskText) : that.myTaskText != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = getLine();
    result = 31 * result + getStart();
    result = 31 * result + (getHint() != null ? getHint().hashCode() : 0);
    result = 31 * result + (getPossibleAnswer() != null ? getPossibleAnswer().hashCode() : 0);
    result = 31 * result + getLength();
    result = 31 * result + getIndex();
    result = 31 * result + (myTaskText != null ? myTaskText.hashCode() : 0);
    return result;
  }
}
