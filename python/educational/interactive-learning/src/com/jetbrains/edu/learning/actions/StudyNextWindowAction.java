package com.jetbrains.edu.learning.actions;

import com.intellij.icons.AllIcons;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.StudyUtils;
import org.jetbrains.annotations.NotNull;

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
  protected AnswerPlaceholder getNextAnswerPlaceholder(@NotNull final AnswerPlaceholder window) {
    int index = window.getIndex();
    List<AnswerPlaceholder> windows = window.getTaskFile().getAnswerPlaceholders();
    if (StudyUtils.indexIsValid(index, windows)) {
      int newIndex = index + 1;
        return newIndex == windows.size() ? windows.get(0) : windows.get(newIndex);
    }
    return null;
  }
}
