package com.jetbrains.edu.learning.courseFormat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AnswerPlaceholderSubtaskInfo {
  private static final Logger LOG = Logger.getInstance(AnswerPlaceholderSubtaskInfo.class);

  @SerializedName("hints")
  @Expose private List<String> myHints = new ArrayList<>();

  @SerializedName("possible_answer")
  @Expose private String possibleAnswer = "";

  @Expose private String myPlaceholderText;

  private String myAnswer = "";
  private boolean mySelected = false;
  private StudyStatus myStatus = StudyStatus.Unchecked;
  @Expose private boolean myHasFrame = true;
  @Expose private boolean myNeedInsertText = false;

  public StudyStatus getStatus() {
    return myStatus;
  }

  public void setStatus(StudyStatus status) {
    myStatus = status;
  }

  public void addHint(@NotNull final String text) {
    myHints.add(text);
  }

  public void removeHint(int i) {
    if (i < myHints.size()) {
      myHints.remove(i);
    }
    else {
      LOG.warn("Trying to remove nonexistent hint. Hint to remove number: " + i + " number of hints: " + myHints.size());
    }
  }

  public List<String> getHints() {
    return myHints;
  }

  public void setHints(List<String> hints) {
    myHints = hints;
  }

  public String getPossibleAnswer() {
    return possibleAnswer;
  }

  public void setPossibleAnswer(String possibleAnswer) {
    this.possibleAnswer = possibleAnswer;
  }

  public String getPlaceholderText() {
    return myPlaceholderText;
  }

  public void setPlaceholderText(String placeholderText) {
    myPlaceholderText = placeholderText;
  }

  public boolean getSelected() {
    return mySelected;
  }

  public void setSelected(boolean selected) {
    mySelected = selected;
  }

  public boolean isHasFrame() {
    return myHasFrame;
  }

  public void setHasFrame(boolean hasFrame) {
    myHasFrame = hasFrame;
  }

  public boolean isNeedInsertText() {
    return myNeedInsertText;
  }

  public void setNeedInsertText(boolean needInsertText) {
    myNeedInsertText = needInsertText;
  }

  public String getAnswer() {
    return myAnswer;
  }

  public void setAnswer(String answer) {
    myAnswer = answer;
  }
}
