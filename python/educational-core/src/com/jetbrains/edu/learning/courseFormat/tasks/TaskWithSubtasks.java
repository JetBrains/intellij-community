package com.jetbrains.edu.learning.courseFormat.tasks;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudySubtaskUtils;
import com.jetbrains.edu.learning.checker.StudyCheckUtils;
import com.jetbrains.edu.learning.checker.StudyTaskChecker;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class TaskWithSubtasks extends Task {
  private int myActiveSubtaskIndex = 0;
  @SerializedName("last_subtask_index")
  @Expose private int myLastSubtaskIndex = 0;

  public TaskWithSubtasks() {}

  public TaskWithSubtasks(@NotNull final String name) {
    super(name);
  }

  public TaskWithSubtasks(Task task) {
    copyTaskParameters(task);
  }

  public int getActiveSubtaskIndex() {
    return myActiveSubtaskIndex;
  }

  public void setActiveSubtaskIndex(int activeSubtaskIndex) {
    myActiveSubtaskIndex = activeSubtaskIndex;
  }

  public int getLastSubtaskIndex() {
    return myLastSubtaskIndex;
  }

  public void setLastSubtaskIndex(int lastSubtaskIndex) {
    myLastSubtaskIndex = lastSubtaskIndex;
  }

  public void setStatus(StudyStatus status) {
    for (TaskFile taskFile : taskFiles.values()) {
      for (AnswerPlaceholder placeholder : taskFile.getActivePlaceholders()) {
        placeholder.setStatus(status);
      }
    }
    if (status == StudyStatus.Solved && activeSubtaskNotLast()) {
      if (myStatus == StudyStatus.Failed) {
        myStatus = StudyStatus.Unchecked;
      }
    }
  }

  public boolean activeSubtaskNotLast() {
    return getActiveSubtaskIndex() != getLastSubtaskIndex();
  }

  public String getTaskType() {
    return "subtasks";
  }

  @Override
  public StudyTaskChecker getChecker(@NotNull Project project) {
    return new StudyTaskChecker<TaskWithSubtasks>(this, project) {
      @Override
      public void onTaskSolved(@NotNull String message) {
        boolean hasMoreSubtasks = myTask.activeSubtaskNotLast();
        final int activeSubtaskIndex = myTask.getActiveSubtaskIndex();
        int visibleSubtaskIndex = activeSubtaskIndex + 1;
        ApplicationManager.getApplication().invokeLater(() -> {
          int subtaskSize = myTask.getLastSubtaskIndex() + 1;
          String resultMessage = !hasMoreSubtasks ? message : "Subtask " + visibleSubtaskIndex + "/" + subtaskSize + " solved";
          StudyCheckUtils.showTestResultPopUp(resultMessage, MessageType.INFO.getPopupBackground(), myProject);
          if (hasMoreSubtasks) {
            int nextSubtaskIndex = activeSubtaskIndex + 1;
            StudySubtaskUtils.switchStep(myProject, myTask, nextSubtaskIndex);
            rememberAnswers(nextSubtaskIndex, myTask);
          }
        });
      }

      private void rememberAnswers(int nextSubtaskIndex, @NotNull TaskWithSubtasks task) {
        VirtualFile taskDir = task.getTaskDir(myProject);
        if (taskDir == null) {
          return;
        }
        VirtualFile srcDir = taskDir.findChild(EduNames.SRC);
        if (srcDir != null) {
          taskDir = srcDir;
        }
        for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
          TaskFile taskFile = entry.getValue();
          VirtualFile virtualFile = taskDir.findFileByRelativePath(entry.getKey());
          if (virtualFile == null) {
            continue;
          }
          Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
          if (document == null) {
            continue;
          }
          for (AnswerPlaceholder placeholder : taskFile.getActivePlaceholders()) {
            if (placeholder.getSubtaskInfos().containsKey(nextSubtaskIndex - 1)) {
              int offset = placeholder.getOffset();
              String answer = document.getText(TextRange.create(offset, offset + placeholder.getRealLength()));
              placeholder.getSubtaskInfos().get(nextSubtaskIndex - 1).setAnswer(answer);
            }
          }
        }
      }
    };
  }
}
