package com.jetbrains.tmp.learning.actions;

import com.jetbrains.tmp.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.tmp.learning.StudyUtils;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * author: liana
 * data: 6/30/14.
 */
public class StudyPrevWindowAction extends StudyWindowNavigationAction {
  public static final String ACTION_ID = "SCore.PrevWindowAction";
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

  @NotNull
  @Override
  public String getActionId() {
    return ACTION_ID;
  }

  @Nullable
  @Override
  public String[] getShortcuts() {
    return new String[]{SHORTCUT};
  }
}
