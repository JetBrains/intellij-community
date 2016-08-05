package com.jetbrains.edu.learning;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.jetbrains.edu.learning.checker.StudyCheckUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class StudyStepUtils {
  private StudyStepUtils() {
  }

  private static final Logger LOG = Logger.getInstance(StudyStepUtils.class);

  public static void switchStep(@NotNull Project project, @NotNull Task task, int toStep) {
    int fromStepIndex = task.getActiveStepIndex();
    if (fromStepIndex == toStep) {
      return;
    }

    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }
    VirtualFile srcDir = taskDir.findChild(EduNames.SRC);
    if (srcDir != null) {
      taskDir = srcDir;
    }
    for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
      String name = entry.getKey();
      //VirtualFile virtualFile = taskDir.findChild(name);
      VirtualFile virtualFile = taskDir.findFileByRelativePath(name);
      if (virtualFile == null) {
        continue;
      }
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      if (document == null) {
        continue;
      }
      //todo: move to more proper place?
      Course course = task.getLesson().getCourse();
      TaskFile fromStepFile = getStepTaskFile(task, fromStepIndex, name);
      TaskFile toStepFile = getStepTaskFile(task, toStep, name);
      if (fromStepFile != null && toStepFile != null) {
        for (AnswerPlaceholder placeholder : fromStepFile.getAnswerPlaceholders()) {
          AnswerPlaceholder toPlaceholder = toStepFile.getAnswerPlaceholder(placeholder.getOffset());
          if (toPlaceholder != null) {
            if (!EduNames.STUDY.equals(course.getCourseMode())) {
              placeholder.setSavedText(placeholder.getPossibleAnswer());
              EduUtils.replaceAnswerPlaceholder(project, document, placeholder, toPlaceholder.getPossibleAnswer());
              toPlaceholder.setSavedText("");
            }
          }
        }
      }
      if (fromStepIndex < toStep) {
        // get new placeholders and paste them for all the steps from from to toStep
        for (int i = fromStepIndex + 1; i <= toStep; i++) {
          TaskFile stepTaskFile = getStepTaskFile(task, i, name);
          if (stepTaskFile == null) {
            continue;
          }
          for (AnswerPlaceholder placeholder : stepTaskFile.getAnswerPlaceholders()) {
            if (placeholder.isVisibleAtPrevStep()) {
              continue;
            }
            String savedAnswer = placeholder.getSavedText();
            if (savedAnswer.isEmpty()) {
              placeholder.setLength(0);
            }
            placeholder.setSavedText("");
            int offset = placeholder.getOffset();
            EduUtils.replaceAnswerPlaceholder(project, document, offset, offset, savedAnswer.isEmpty() ? placeholder.getTaskText() : savedAnswer);
          }
        }
      }
      if (fromStepIndex > toStep) {
        for (int i = toStep + 1; i <= fromStepIndex; i++) {
          TaskFile stepTaskFile = getStepTaskFile(task, i, name);
          if (stepTaskFile == null) {
            continue;
          }
          for (AnswerPlaceholder placeholder : stepTaskFile.getAnswerPlaceholders()) {
            if (placeholder.isVisibleAtPrevStep()) {
              continue;
            }
            placeholder.setSavedText(placeholder.getUseLength()
                                     ? document.getText(
              TextRange.create(placeholder.getOffset(), placeholder.getOffset() + placeholder.getRealLength()))
                                     : placeholder.getPossibleAnswer());
            EduUtils.replaceAnswerPlaceholder(project, document, placeholder, "");
          }
        }
      }
      EditorNotifications.getInstance(project).updateNotifications(virtualFile);
    }
    task.setActiveStepIndex(toStep);
    update(project, task, taskDir);
  }

  private static void update(@NotNull Project project, @NotNull Task task, VirtualFile taskDir) {
    StudyCheckUtils.drawAllPlaceholders(project, task, taskDir);
    ProjectView.getInstance(project).refresh();
    StudyToolWindow toolWindow = StudyUtils.getStudyToolWindow(project);
    if (toolWindow != null) {
      String text = StudyUtils.getTaskTextFromTask(taskDir, task);
      if (text == null) {
        toolWindow.setEmptyText(project);
        return;
      }
      toolWindow.setTaskText(text, taskDir, project);
    }
  }

  public static void deleteStep(@NotNull Project project, @NotNull Task task, int index) {
    //TODO: delete not only the last step
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }

    ArrayList<VirtualFile> filesToDelete = new ArrayList<>();
    for (VirtualFile file : taskDir.getChildren()) {
      String stepSuffix = EduNames.STEP_MARKER + index;
      if (file.getName().contains(stepSuffix)) {
        filesToDelete.add(file);
      }
    }
    for (VirtualFile file : filesToDelete) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          file.delete(StudyStepUtils.class);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      });
    }

    task.getAdditionalSteps().remove(index);
    if (task.getActiveStepIndex() == index) {
      switchStep(project, task, index - 1);
    }
  }

  @Nullable
  public static TaskFile getStepTaskFile(@NotNull Task task, int stepIndex, String name) {
    if (stepIndex == -1) {
      return task.getTaskFile(name);
    }
    return task.getAdditionalSteps().get(stepIndex).getTaskFiles().get(name);
  }

  /**
   * @return map from step index to task file
   */
  public static Map<Integer, TaskFile> getTaskFile(@NotNull Task task, String name) {
    Map<Integer, TaskFile> taskFiles = new HashMap<>();
    TaskFile initialTaskFile = task.getTaskFile(name);
    if (initialTaskFile == null) {
      return Collections.emptyMap();
    }
    taskFiles.put(-1, initialTaskFile);
    if (task.getAdditionalSteps().isEmpty()) {
      return taskFiles;
    }
    for (int i = 0; i < task.getAdditionalSteps().size(); i++) {
      TaskFile file = task.getAdditionalSteps().get(i).getTaskFiles().get(name);
      if (file == null) {
        continue;
      }
      taskFiles.put(i, file);
    }
    return taskFiles;
  }
}
