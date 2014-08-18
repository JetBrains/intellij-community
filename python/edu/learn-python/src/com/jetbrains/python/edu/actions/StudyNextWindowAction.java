package com.jetbrains.python.edu.actions;

import com.jetbrains.python.edu.StudyUtils;
import com.jetbrains.python.edu.course.TaskWindow;
import icons.StudyIcons;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * move caret to next task window
 */
public class StudyNextWindowAction extends StudyWindowNavigationAction {
  public static final String ACTION_ID = "NextWindow";
  public static final String SHORTCUT = "ctrl pressed PERIOD";
  public static final String SHORTCUT2 = "ctrl pressed ENTER";

  public StudyNextWindowAction() {
    super("NextWindowAction", "Select next window", StudyIcons.Next);
  }

  @Override
  protected TaskWindow getNextTaskWindow(@NotNull final TaskWindow window) {
    int index = window.getIndex();
    List<TaskWindow> windows = window.getTaskFile().getTaskWindows();
    if (StudyUtils.indexIsValid(index, windows)) {
      int newIndex = index + 1;
        return newIndex == windows.size() ? windows.get(0) : windows.get(newIndex);
    }
    return null;
  }
}
