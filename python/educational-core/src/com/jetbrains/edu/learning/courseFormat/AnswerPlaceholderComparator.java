package com.jetbrains.edu.learning.courseFormat;

import java.util.Comparator;

public class AnswerPlaceholderComparator implements Comparator<AnswerPlaceholder> {
  @Override
  public int compare(AnswerPlaceholder o1, AnswerPlaceholder answerPlaceholder) {
    return o1.getOffset() - answerPlaceholder.getOffset();
  }

  @Override
  public boolean equals(Object obj) {
    return false;
  }
}