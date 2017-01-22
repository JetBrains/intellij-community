package com.jetbrains.edu.learning.courseFormat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class AdaptiveTaskParameters {
  @Expose @SerializedName("choice_variants") private List<String> myChoiceVariants = new ArrayList<>();
  @Expose @SerializedName("is_multichoice") private boolean myIsMultipleChoice;
  @SerializedName("selected_variants") private List<Integer> mySelectedVariants = new ArrayList<>();
  @Expose @SerializedName("is_theory_task") private boolean isTheoryTask = false;

  public List<Integer> getSelectedVariants() {
    return mySelectedVariants;
  }

  public void setSelectedVariants(List<Integer> selectedVariants) {
    mySelectedVariants = selectedVariants;
  }

  public boolean isMultipleChoice() {
    return myIsMultipleChoice;
  }

  public void setMultipleChoice(boolean multipleChoice) {
    myIsMultipleChoice = multipleChoice;
  }

  public List<String> getChoiceVariants() {
    return myChoiceVariants;
  }

  public void setChoiceVariants(List<String> choiceVariants) {
    myChoiceVariants = choiceVariants;
  }

  public boolean isTheoryTask() {
    return isTheoryTask;
  }

  public void setTheoryTask(boolean theoryTask) {
    isTheoryTask = theoryTask;
  }
}
