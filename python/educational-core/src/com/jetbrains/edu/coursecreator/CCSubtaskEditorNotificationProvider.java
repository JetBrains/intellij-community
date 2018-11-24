package com.jetbrains.edu.coursecreator;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.jetbrains.edu.coursecreator.actions.CCNewSubtaskAction;
import com.jetbrains.edu.learning.StudySubtaskUtils;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholderSubtaskInfo;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.PyCharmTask;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.*;

import static com.jetbrains.edu.coursecreator.CCUtils.renameFiles;

public class CCSubtaskEditorNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("edu.coursecreator.subtask");
  public static final String SWITCH_SUBTASK = "Switch subtask";
  public static final Integer ADD_SUBTASK_ID = -1;
  private static final Logger LOG = Logger.getInstance(CCSubtaskEditorNotificationProvider.class);
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
    if (task instanceof TaskWithSubtasks) {
      final TaskWithSubtasks withSubtasks = (TaskWithSubtasks)task;
      EditorNotificationPanel panel = new EditorNotificationPanel(EditorColors.GUTTER_BACKGROUND);
      String header = (isTestFile ? "test" : "task") + " file";
      int activeSubtaskIndex = withSubtasks.getActiveSubtaskIndex() + 1;
      int subtaskSize = withSubtasks.getLastSubtaskIndex() + 1;
      panel.setText("This is a " + header + " for " + EduNames.SUBTASK + " " + activeSubtaskIndex + "/" + subtaskSize);
      panel
        .createActionLabel(SWITCH_SUBTASK, () -> createPopup(withSubtasks, myProject).show(RelativePoint.getSouthEastOf(panel)));
      return panel;
    }
    return null;
  }

  @NotNull
  public static ListPopup createPopup(@NotNull TaskWithSubtasks task, @NotNull Project project) {
    ArrayList<Integer> values = new ArrayList<>();
    for (int i = 0; i <= task.getLastSubtaskIndex(); i++) {
      values.add(i);
    }
    values.add(ADD_SUBTASK_ID);
    return JBPopupFactory.getInstance().createListPopup(new SwitchSubtaskPopupStep(SWITCH_SUBTASK, values, task, project));
  }

  public static class SwitchSubtaskPopupStep extends BaseListPopupStep<Integer> {
    private final TaskWithSubtasks myTask;
    private final Project myProject;

    public SwitchSubtaskPopupStep(@Nullable String title,
                                  List<Integer> values,
                                  @NotNull TaskWithSubtasks task,
                                  @NotNull Project project) {
      super(title, values);
      myTask = task;
      myProject = project;
    }

    @NotNull
    @Override
    public String getTextFor(Integer value) {
      if (value.equals(ADD_SUBTASK_ID)) {
        return CCNewSubtaskAction.NEW_SUBTASK;
      }
      int subtaskNum = value + 1;
      return " " + EduNames.SUBTASK + " " + subtaskNum;
    }

    @Override
    public Icon getIconFor(Integer value) {
      return value == myTask.getActiveSubtaskIndex() ? AllIcons.Actions.Checked : EmptyIcon.create(AllIcons.Actions.Checked);
    }

    @Override
    public PopupStep onChosen(Integer selectedValue, boolean finalChoice) {
      if (finalChoice) {
        if (selectedValue.equals(ADD_SUBTASK_ID)) {
          return doFinalStep(() -> CCNewSubtaskAction.addSubtask(myTask, myProject));
        }
        StudySubtaskUtils.switchStep(myProject, myTask, selectedValue);
      }
      else {
        if (hasSubstep(selectedValue)) {
          return new ActionsPopupStep(myTask, selectedValue, myProject);
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

  private static class ActionsPopupStep extends BaseListPopupStep<String> {

    public static final String SELECT = "Select";
    public static final String DELETE = "Delete";
    private final TaskWithSubtasks myTask;
    private final int mySubtaskIndex;
    private final Project myProject;

    public ActionsPopupStep(@NotNull TaskWithSubtasks task, int subtaskIndex, @NotNull Project project) {
      super(null, Arrays.asList(SELECT, DELETE));
      myTask = task;
      mySubtaskIndex = subtaskIndex;
      myProject = project;
    }

    @Override
    public PopupStep onChosen(String selectedValue, boolean finalChoice) {
      if (finalChoice) {
        if (selectedValue.equals(SELECT)) {
          StudySubtaskUtils.switchStep(myProject, myTask, mySubtaskIndex);
        }
        else {
          return deleteSubtask();
        }
      }
      return super.onChosen(selectedValue, finalChoice);
    }

    @Nullable
    private PopupStep deleteSubtask() {
      final int lastSubtaskIndex = myTask.getLastSubtaskIndex();
      for (TaskFile taskFile : myTask.getTaskFiles().values()) {
        List<AnswerPlaceholder> emptyPlaceholders = new ArrayList<>();
        for (AnswerPlaceholder placeholder : taskFile.getAnswerPlaceholders()) {
          Map<Integer, AnswerPlaceholderSubtaskInfo> infos = placeholder.getSubtaskInfos();
          if (infos.containsKey(mySubtaskIndex)) {
            infos.remove(mySubtaskIndex);
            if (infos.isEmpty()) {
              emptyPlaceholders.add(placeholder);
            }
          }
        }
        taskFile.getAnswerPlaceholders().removeAll(emptyPlaceholders);
      }
      VirtualFile taskDir = myTask.getTaskDir(myProject);
      if (taskDir == null) {
        return FINAL_CHOICE;
      }
      deleteSubtaskFiles(taskDir);
      if (mySubtaskIndex != lastSubtaskIndex) {
        renameFiles(taskDir, myProject, mySubtaskIndex);
        updateInfoIndexes();
      }
      myTask.setLastSubtaskIndex(lastSubtaskIndex - 1);
      int activeSubtaskIndex = myTask.getActiveSubtaskIndex();
      if (mySubtaskIndex != 0 && activeSubtaskIndex == mySubtaskIndex) {
        StudySubtaskUtils.switchStep(myProject, myTask, mySubtaskIndex - 1);
      }
      if (activeSubtaskIndex > mySubtaskIndex) {
        myTask.setActiveSubtaskIndex(activeSubtaskIndex - 1);
      }
      StudySubtaskUtils.updateUI(myProject, myTask, true);
      for (VirtualFile file : FileEditorManager.getInstance(myProject).getOpenFiles()) {
        EditorNotifications.getInstance(myProject).updateNotifications(file);
      }
      if (lastSubtaskIndex == 1) {
        convertToTask();
      }
      return FINAL_CHOICE;
    }

    private void convertToTask() {
      final Lesson lesson = myTask.getLesson();
      final List<Task> list = lesson.getTaskList();
      final int i = list.indexOf(myTask);
      final Task task = new PyCharmTask();
      task.copyTaskParameters(myTask);
      for (TaskFile taskFile : task.getTaskFiles().values()) {
        taskFile.setTask(task);
      }
      list.set(i, task);
      renameFiles(task.getTaskDir(myProject), myProject, -2);
    }

    private void updateInfoIndexes() {
      for (TaskFile taskFile : myTask.getTaskFiles().values()) {
        for (AnswerPlaceholder placeholder : taskFile.getAnswerPlaceholders()) {
          List<Integer> filtered = ContainerUtil.filter(placeholder.getSubtaskInfos().keySet(), index -> index > mySubtaskIndex);
          Map<Integer, AnswerPlaceholderSubtaskInfo> savedInfos = new HashMap<>();
          for (Integer index : filtered) {
            savedInfos.put(index, placeholder.getSubtaskInfos().get(index));
            placeholder.getSubtaskInfos().remove(index);
          }
          for (Integer index : filtered) {
            placeholder.getSubtaskInfos().put(index - 1, savedInfos.get(index));
          }
        }
      }
    }

    private void deleteSubtaskFiles(VirtualFile taskDir) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        List<VirtualFile> filesToDelete = new ArrayList<>();
        for (VirtualFile file : taskDir.getChildren()) {
          int index = CCUtils.getSubtaskIndex(myProject, file);
          if (index != -1 && mySubtaskIndex == index) {
            filesToDelete.add(file);
          }
        }
        for (VirtualFile file : filesToDelete) {
          try {
            file.delete(myProject);
          }
          catch (IOException e) {
            LOG.info(e);
          }
        }
      });
    }
  }
}