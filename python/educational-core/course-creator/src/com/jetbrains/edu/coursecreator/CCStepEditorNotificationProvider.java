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
import com.jetbrains.edu.coursecreator.actions.CCNewStepAction;
import com.jetbrains.edu.learning.StudyStepManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CCStepEditorNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("edu.coursecreator.step");
  public static final String SWITCH_STEP = "Switch step";
  public static final Integer ADD_STEP_ID = -2;
  private final Project myProject;

  public CCStepEditorNotificationProvider(Project project) {
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
    if (task == null || task.getAdditionalSteps().isEmpty()) {
      return null;
    }
    int activeStepIndex = task.getActiveStepIndex() + 2;
    int stepsNum = task.getAdditionalSteps().size() + 1;
    EditorNotificationPanel panel = new EditorNotificationPanel() {
      @Override
      public Color getBackground() {
        Color color = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.GUTTER_BACKGROUND);
        return color == null ? super.getBackground() : color;
      }
    };
    String header = isTestFile ? "test" : "task file";
    panel.setText("This is " + header + " for " + EduNames.STEP + " " + activeStepIndex + "/" + stepsNum);
    panel.createActionLabel(SWITCH_STEP, () -> {
      ArrayList<Integer> values = new ArrayList<>();
      values.add(-1);
      for (int i = 0; i < task.getAdditionalSteps().size(); i++) {
        values.add(i);
      }
      values.add(ADD_STEP_ID);
      JBPopupFactory.getInstance().createListPopup(new SwitchStepPopupStep(SWITCH_STEP, values, task, file)).show(RelativePoint.getSouthEastOf(panel));
    });
    return panel;
  }

  private class SwitchStepPopupStep extends BaseListPopupStep<Integer> {
    private final Task myTask;
    private final VirtualFile myFile;

    public SwitchStepPopupStep(@Nullable String title,
                               List<Integer> values,
                               Task task, VirtualFile file) {
      super(title, values);
      myTask = task;
      myFile = file;
    }

    @NotNull
    @Override
    public String getTextFor(Integer value) {
      if (value.equals(ADD_STEP_ID)) {
        return CCNewStepAction.NEW_STEP;
      }
      int stepNum = value + 2;
      String text = EduNames.STEP + " " + stepNum;
      if (value == myTask.getActiveStepIndex()) {
        text +=  " (current step)";
      }
      return text;
    }

    @Override
    public PopupStep onChosen(Integer selectedValue, boolean finalChoice) {
      if (finalChoice) {
        if (selectedValue.equals(ADD_STEP_ID)) {
          return doFinalStep(() -> CCNewStepAction.addStep(myFile, myProject));
        }
        return doFinalStep(() -> StudyStepManager.switchStep(myProject, myTask, selectedValue));
      } else {
        if (hasSubstep(selectedValue)) {
          return new ActionsPopupStep(myTask, selectedValue);
        }
      }
      return super.onChosen(selectedValue, false);
    }

    @Override
    public boolean hasSubstep(Integer selectedValue) {
      return !selectedValue.equals(ADD_STEP_ID);
    }

    @Override
    public int getDefaultOptionIndex() {
      return myTask.getActiveStepIndex() + 1;
    }

    @Nullable
    @Override
    public ListSeparator getSeparatorAbove(Integer value) {
      return value.equals(ADD_STEP_ID) ? new ListSeparator(): null;
    }
  }

  private class ActionsPopupStep extends BaseListPopupStep<String> {

    public static final String SELECT = "Select";
    public static final String DELETE = "Delete";
    private final Task myTask;
    private final int myStepIndex;

    public ActionsPopupStep(Task task, int stepIndex) {
      super(null, Arrays.asList(SELECT, DELETE));
      myTask = task;
      myStepIndex = stepIndex;
    }

    @Override
    public PopupStep onChosen(String selectedValue, boolean finalChoice) {
      if (finalChoice) {
        if (selectedValue.equals(SELECT)) {
          StudyStepManager.switchStep(myProject, myTask, myStepIndex);
        } else {
          if (myStepIndex != myTask.getAdditionalSteps().size() - 1) {
            //TODO: implement
          } else {
            StudyStepManager.deleteStep(myProject, myTask, myStepIndex);
          }
          return FINAL_CHOICE;
        }
      }
      return super.onChosen(selectedValue, finalChoice);
    }
  }
}
