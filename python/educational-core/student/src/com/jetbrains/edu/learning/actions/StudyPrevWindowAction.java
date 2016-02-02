package com.jetbrains.edu.learning.actions;

import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.StudyUtils;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * author: liana
 * data: 6/30/14.
 */
public class StudyPrevWindowAction extends StudyWindowNavigationAction {
  public static final String ACTION_ID = "PrevWindowAction";
  public static final String SHORTCUT = "ctrl shift pressed COMMA";

  public StudyPrevWindowAction() {
    super("Navigate to the Previous Answer Placeholder", "Navigate to the previous answer placeholder", InteractiveLearningIcons.Prev);
  }


  @Nullable
  @Override
  protected AnswerPlaceholder getNextAnswerPlaceholder(@NotNull final AnswerPlaceholder window) {
    int prevIndex = window.getIndex() - 1;
    List<AnswerPlaceholder> windows = window.getTaskFile().getAnswerPlaceholders();
    if (StudyUtils.indexIsValid(prevIndex, windows)) {
      return windows.get(prevIndex);
    }
    return null;
  }
}
