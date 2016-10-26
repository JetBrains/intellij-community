package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.awt.RelativePoint;
import com.jetbrains.edu.coursecreator.actions.CCNewSubtaskAction;
import com.jetbrains.edu.learning.StudySubtaskUtils;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CCSubtaskEditorNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("edu.coursecreator.subtask");
  public static final String SWITCH_SUBTASK = "Switch subtask";
  public static final Integer ADD_SUBTASK_ID = -1;
  private final Project myProject;

  public CCSubtaskEditorNotificationProvider(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (!CCUtils.isCourseCreator(myProject)) {
      return null;
    }
    boolean isTestFile = CCUtils.isTestsFile(myProject, file);
    if (!isTestFile && StudyUtils.getTaskFile(myProject, file) == null) {
      return null;
    }
    Task task = StudyUtils.getTaskForFile(myProject, file);
    if (task == null || !task.hasSubtasks()) {
      return null;
    }
    EditorNotificationPanel panel = new EditorNotificationPanel() {
      @Override
      public Color getBackground() {
        Color color = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.GUTTER_BACKGROUND);
        return color == null ? super.getBackground() : color;
      }
    };
    String header = isTestFile ? "test" : "task file";
    int activeSubtaskIndex = task.getActiveSubtaskIndex() + 1;
    int subtaskSize = task.getLastSubtaskIndex() + 1;
    panel.setText("This is " + header + " for " + EduNames.SUBTASK + " " + activeSubtaskIndex + "/" + subtaskSize);
    panel.createActionLabel(SWITCH_SUBTASK, () -> {
      ArrayList<Integer> values = new ArrayList<>();
      for (int i = 0; i <= task.getLastSubtaskIndex(); i++) {
        values.add(i);
      }
      values.add(ADD_SUBTASK_ID);
      JBPopupFactory.getInstance().createListPopup(new SwitchSubtaskPopupStep(SWITCH_SUBTASK, values, task, file)).show(RelativePoint.getSouthEastOf(panel));
    });
    return panel;
  }

  private class SwitchSubtaskPopupStep extends BaseListPopupStep<Integer> {
    private final Task myTask;
    private final VirtualFile myFile;

    public SwitchSubtaskPopupStep(@Nullable String title,
                                  List<Integer> values,
                                  Task task, VirtualFile file) {
      super(title, values);
      myTask = task;
      myFile = file;
    }

    @NotNull
    @Override
    public String getTextFor(Integer value) {
      if (value.equals(ADD_SUBTASK_ID)) {
        return CCNewSubtaskAction.NEW_SUBTASK;
      }
      int subtaskNum = value + 1;
      String text = EduNames.SUBTASK + " " + subtaskNum;
      if (value == myTask.getActiveSubtaskIndex()) {
        text +=  " (selected)";
      }
      return text;
    }

    @Override
    public PopupStep onChosen(Integer selectedValue, boolean finalChoice) {
      if (finalChoice) {
        if (selectedValue.equals(ADD_SUBTASK_ID)) {
          return doFinalStep(() -> CCNewSubtaskAction.addSubtask(myFile, myProject));
        }
        StudySubtaskUtils.switchStep(myProject, myTask, selectedValue);
      } else {
        if (hasSubstep(selectedValue)) {
          return new ActionsPopupStep(myTask, selectedValue);
        }
      }
      return super.onChosen(selectedValue, false);
    }

    @Override
    public boolean hasSubstep(Integer selectedValue) {
      return !selectedValue.equals(ADD_SUBTASK_ID);
    }

    @Override
    public int getDefaultOptionIndex() {
      return myTask.getActiveSubtaskIndex();
    }

    @Nullable
    @Override
    public ListSeparator getSeparatorAbove(Integer value) {
      return value.equals(ADD_SUBTASK_ID) ? new ListSeparator() : null;
    }
  }

  private class ActionsPopupStep extends BaseListPopupStep<String> {

    public static final String SELECT = "Select";
    public static final String DELETE = "Delete";
    private final Task myTask;
    private final int mySubtaskIndex;

    public ActionsPopupStep(Task task, int subtaskIndex) {
      super(null, Arrays.asList(SELECT, DELETE));
      myTask = task;
      mySubtaskIndex = subtaskIndex;
    }

    @Override
    public PopupStep onChosen(String selectedValue, boolean finalChoice) {
      if (finalChoice) {
        if (selectedValue.equals(SELECT)) {
          StudySubtaskUtils.switchStep(myProject, myTask, mySubtaskIndex);
        } else {
          if (mySubtaskIndex != myTask.getLastSubtaskIndex()) {
            //TODO: implement
          } else {
            //TODO: delete last subtask
          }
          return FINAL_CHOICE;
        }
      }
      return super.onChosen(selectedValue, finalChoice);
    }
  }
}