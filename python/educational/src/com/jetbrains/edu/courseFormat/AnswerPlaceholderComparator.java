package com.jetbrains.edu.courseFormat;

import java.util.Comparator;

public class AnswerPlaceholderComparator implements Comparator<AnswerPlaceholder> {
  @Override
  public int compare(AnswerPlaceholder o1, AnswerPlaceholder answerPlaceholder) {
    final int line = o1.getLine();
    int lineDiff = line - answerPlaceholder.getLine();
    if (lineDiff == 0) {
      return o1.getStart() - answerPlaceholder.getStart();
    }
    return lineDiff;
  }

  @Override
  public boolean equals(Object obj) {
    return false;
  }
}