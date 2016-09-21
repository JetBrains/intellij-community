package com.jetbrains.edu.learning.courseFormat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Implementation of windows which user should type in
 */

public class AnswerPlaceholder {

  @SerializedName("offset")
  @Expose private int myOffset = -1;

  @Expose private int length = -1;

  private int myIndex = -1;
  private MyInitialState myInitialState;
  private StudyStatus myStatus = StudyStatus.Unchecked;
  private boolean mySelected = false;
  private boolean myUseLength = true;

  @Transient private TaskFile myTaskFile;

  @Expose private Map<Integer, AnswerPlaceholderSubtaskInfo> mySubtaskInfos = new HashMap<>();
  public AnswerPlaceholder() {
  }

  public void initAnswerPlaceholder(final TaskFile file, boolean isRestarted) {
    if (!isRestarted) {
      setInitialState(new MyInitialState(myOffset, length));
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

  /**
   * in actions {@link AnswerPlaceholder#getRealLength()} should be used
   */
  public int getLength() {
    return length;
  }

  public void setLength(int length) {
    this.length = length;
  }

  @Transient
  public String getPossibleAnswer() {
    return getActiveSubtaskInfo().getPossibleAnswer();
  }

  @Transient
  public void setPossibleAnswer(String possibleAnswer) {
    getActiveSubtaskInfo().setPossibleAnswer(possibleAnswer);
  }

  public MyInitialState getInitialState() {
    return myInitialState;
  }

  public void setInitialState(MyInitialState initialState) {
    myInitialState = initialState;
  }

  @Transient
  public String getTaskText() {
    return getActiveSubtaskInfo().getPlaceholderText();
  }

  @Transient
  public void setTaskText(String taskText) {
    getActiveSubtaskInfo().setPlaceholderText(taskText);
  }

  @Transient
  public TaskFile getTaskFile() {
    return myTaskFile;
  }

  @Transient
  public void setTaskFile(TaskFile taskFile) {
    myTaskFile = taskFile;
  }

  public int getPossibleAnswerLength() {
    return getPossibleAnswer().length();
  }

  /**
   * Returns window to its initial state
   */
  public void reset() {
    myOffset = myInitialState.getOffset();
    length = myInitialState.getLength();
  }

  public StudyStatus getStatus() {
    return myStatus;
  }

  public void setStatus(StudyStatus status) {
    myStatus = status;
  }

  public boolean getSelected() {
    return mySelected;
  }

  public void setSelected(boolean selected) {
    mySelected = selected;
  }

  public void init() {
    setInitialState(new MyInitialState(myOffset, getTaskText().length()));
  }

  public boolean getUseLength() {
    return myUseLength;
  }

  /**
   * @return length or possible answer length
   */
  public int getRealLength() {
    return myUseLength ? getLength() : getPossibleAnswerLength();
  }

  public void setUseLength(boolean useLength) {
    myUseLength = useLength;
  }

  public int getOffset() {
    return myOffset;
  }

  public void setOffset(int offset) {
    myOffset = offset;
  }

  @Transient
  public List<String> getHints() {
    return getActiveSubtaskInfo().getHints();
  }

  @Transient
  public void setHints(@NotNull final List<String> hints) {
   getActiveSubtaskInfo().setHints(hints);
  }

  public void addHint(@NotNull final String text) {
    getActiveSubtaskInfo().addHint(text);
  }

  public void removeHint(int i) {
    getActiveSubtaskInfo().removeHint(i);
  }

  public Map<Integer, AnswerPlaceholderSubtaskInfo> getSubtaskInfos() {
    return mySubtaskInfos;
  }

  public void setSubtaskInfos(Map<Integer, AnswerPlaceholderSubtaskInfo> subtaskInfos) {
    mySubtaskInfos = subtaskInfos;
  }

  public static class MyInitialState {
    private int length = -1;
    private int offset = -1;

    public MyInitialState() {
    }

    public MyInitialState(int initialOffset, int length) {
      this.offset = initialOffset;
      this.length = length;
    }

    public int getLength() {
      return length;
    }

    public void setLength(int length) {
      this.length = length;
    }

    public int getOffset() {
      return offset;
    }

    public void setOffset(int offset) {
      this.offset = offset;
    }
  }

  public AnswerPlaceholderSubtaskInfo getActiveSubtaskInfo() {
    if (myTaskFile == null || myTaskFile.getTask() == null) {
      return mySubtaskInfos.get(0);
    }
    int activeStepIndex = myTaskFile.getTask().getActiveStepIndex();
    return mySubtaskInfos.get(activeStepIndex);
  }
}
