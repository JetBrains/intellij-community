package com.jetbrains.edu.learning.courseFormat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnswerPlaceholderSubtaskInfo {
  private static final Logger LOG = Logger.getInstance(AnswerPlaceholderSubtaskInfo.class);
  @SerializedName("hint")
  @Expose private String myHint = "";

  @SerializedName("additional_hints")
  @Expose private List<String> myAdditionalHints = new ArrayList<>();

  @SerializedName("possible_answer")
  @Expose private String possibleAnswer = "";

  @Expose private String myPlaceholderText;
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

  @Transient
  public List<String> getHints() {
    if (myHint.isEmpty() && myAdditionalHints.isEmpty()) return Collections.emptyList();
    final ArrayList<String> result = new ArrayList<>();
    result.add(myHint);
    result.addAll(myAdditionalHints);
    return result;
  }

  @Transient
  public void setHints(@NotNull final List<String> hints) {
    if (hints.isEmpty()) {
      myHint = "";
      myAdditionalHints.clear();
    }
    else {
      myHint = hints.get(0);
      myAdditionalHints = hints.subList(1, hints.size());
    }
  }

  public void addHint(@NotNull final String text) {
    if (myHint.isEmpty() && myAdditionalHints.isEmpty()) {
      myHint = text;
    }
    else {
      myAdditionalHints.add(text);
    }
  }

  public void removeHint(int i) {
    if (i == 0) {
      myHint = "";
    }
    else {
      if (i - 1 <myAdditionalHints.size()) {
        myAdditionalHints.remove(i - 1);
      }
      else {
        LOG.warn("Trying to remove nonexistent hint. Hint to remove number: " + (i - 1) + "number of hints: " + getHints().size());
      }
    }
  }

  @NotNull
  public List<String> getAdditionalHints() {
    return myAdditionalHints;
  }

  public void setAdditionalHints(@Nullable final List<String> additionalHints) {
    myAdditionalHints = additionalHints;
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

  public String getHint() {
    return myHint;
  }

  public void setHint(String hint) {
    myHint = hint;
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
}
