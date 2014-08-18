package com.jetbrains.python.edu.actions;

import com.jetbrains.python.edu.StudyUtils;
import com.jetbrains.python.edu.course.TaskWindow;
import icons.StudyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * author: liana
 * data: 6/30/14.
 */
public class StudyPrevWindowAction extends StudyWindowNavigationAction {
  public static final String ACTION_ID = "PrevWindowAction";
  public static final String SHORTCUT = "ctrl pressed COMMA";

  public StudyPrevWindowAction() {
    super("PrevWindowAction", "Select previous window", StudyIcons.Prev);
  }


  @Nullable
  @Override
  protected TaskWindow getNextTaskWindow(@NotNull final TaskWindow window) {
    int prevIndex = window.getIndex() - 1;
    List<TaskWindow> windows = window.getTaskFile().getTaskWindows();
    if (StudyUtils.indexIsValid(prevIndex, windows)) {
      return windows.get(prevIndex);
    }
    return null;
  }
}
