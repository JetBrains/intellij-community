package com.jetbrains.edu.coursecreator;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;

import java.util.Collection;
import java.util.List;

public class CCTestsUtil {
  public static final String BEFORE_POSTFIX = "_before.txt";
  public static final String AFTER_POSTFIX = "_after.txt";

  private CCTestsUtil() {
  }

  public static boolean comparePlaceholders(AnswerPlaceholder p1, AnswerPlaceholder p2) {
    if (p1.getOffset() != p2.getOffset()) return false;
    if (p1.getRealLength() != p2.getRealLength()) return false;
    if (p1.getPossibleAnswer() != null ? !p1.getPossibleAnswer().equals(p2.getPossibleAnswer()) : p2.getPossibleAnswer() != null) return false;
    if (p1.getTaskText() != null ? !p1.getTaskText().equals(p2.getTaskText()) : p2.getTaskText() != null) return false;
    if (!p1.getHints().equals(p1.getHints())) return false;
    return true;
  }

  public static String getPlaceholderPresentation(AnswerPlaceholder placeholder) {
    return "offset=" + placeholder.getOffset() +
           " length=" + placeholder.getLength() +
           " possibleAnswer=" + placeholder.getPossibleAnswer() +
           " taskText=" + placeholder.getTaskText();
  }

  public static String getPlaceholdersPresentation(List<AnswerPlaceholder> placeholders) {
    Collection<String> transformed = Collections2.transform(placeholders, new Function<AnswerPlaceholder, String>() {
      @Override
      public String apply(AnswerPlaceholder placeholder) {
        return getPlaceholderPresentation(placeholder);
      }
    });
    return "[" + StringUtil.join(transformed, ",") + "]";
  }
}
