package com.jetbrains.edu.learning.actions;

import com.intellij.icons.AllIcons;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * move caret to next task window
 */
public class StudyNextWindowAction extends StudyWindowNavigationAction {
  public static final String ACTION_ID = "NextWindow";
  public static final String SHORTCUT = "ctrl shift pressed PERIOD";
  public static final String SHORTCUT2 = "ctrl pressed ENTER";

  public StudyNextWindowAction() {
    super("Navigate to the Next Answer Placeholder", "Navigate to the next answer placeholder", AllIcons.Actions.Forward);
  }

  @Override
  protected AnswerPlaceholder getTargetPlaceholder(@NotNull final TaskFile taskFile, int offset) {
    final AnswerPlaceholder selectedAnswerPlaceholder = taskFile.getAnswerPlaceholder(offset);
    final List<AnswerPlaceholder> placeholders = taskFile.getAnswerPlaceholders();
    if (selectedAnswerPlaceholder == null) {
      for (AnswerPlaceholder placeholder : placeholders) {
        if (placeholder.getOffset() > offset) {
          return placeholder;
        }
      }
    }
    else {
      int index = selectedAnswerPlaceholder.getIndex();
      if (StudyUtils.indexIsValid(index, placeholders)) {
        int newIndex = index + 1;
        return placeholders.get(newIndex == placeholders.size() ? 0 : newIndex);
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getActionId() {
    return ACTION_ID;
  }

  @Nullable
  @Override
  public String[] getShortcuts() {
    return new String[]{SHORTCUT, SHORTCUT2};
  }
}
