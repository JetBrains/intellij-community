package com.jetbrains.edu.learning;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.edu.learning.checker.StudyCheckUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholderSubtaskInfo;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class StudySubtaskUtils {
  private StudySubtaskUtils() {
  }

  /***
   * @param toSubtaskIndex from 0 to subtaskNum - 1
   */
  public static void switchStep(@NotNull Project project, @NotNull Task task, int toSubtaskIndex) {
    if (toSubtaskIndex == task.getActiveSubtaskIndex()) {
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
    int fromSubtaskIndex = task.getActiveSubtaskIndex();
    for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
      String name = entry.getKey();
      VirtualFile virtualFile = taskDir.findChild(name);
      if (virtualFile == null) {
        continue;
      }
      TaskFile taskFile = entry.getValue();
      updatePlaceholderTexts(project, virtualFile, taskFile, fromSubtaskIndex, toSubtaskIndex);
      EditorNotifications.getInstance(project).updateNotifications(virtualFile);
    }
    task.setActiveSubtaskIndex(toSubtaskIndex);
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

  private static void updatePlaceholderTexts(@NotNull Project project,
                                             @NotNull VirtualFile virtualFile,
                                             @NotNull TaskFile taskFile,
                                             int fromSubtaskIndex,
                                             int toSubtaskIndex) {
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document == null) {
      return;
    }
    taskFile.setTrackLengths(false);
    for (AnswerPlaceholder placeholder : taskFile.getAnswerPlaceholders()) {
      Set<Integer> indexes = placeholder.getSubtaskInfos().keySet();
      Integer minIndex = Collections.min(indexes);
      int visibleLength = placeholder.getVisibleLength(fromSubtaskIndex);
      if (indexes.contains(toSubtaskIndex) && indexes.contains(fromSubtaskIndex)) {
        if (!placeholder.getUseLength()) {
          String replacementText = placeholder.getSubtaskInfos().get(toSubtaskIndex).getPossibleAnswer();
          EduUtils.replaceAnswerPlaceholder(project, document, placeholder, visibleLength, replacementText);
        }
        continue;
      }
      if (fromSubtaskIndex < toSubtaskIndex) {
        if (minIndex > fromSubtaskIndex && minIndex <= toSubtaskIndex) {
          Integer maxIndex = Collections.max(ContainerUtil.filter(indexes, integer -> integer <= toSubtaskIndex));
          AnswerPlaceholderSubtaskInfo maxInfo = placeholder.getSubtaskInfos().get(maxIndex);
          String replacementText = placeholder.getUseLength() ? maxInfo.getPlaceholderText() : maxInfo.getPossibleAnswer();
          EduUtils.replaceAnswerPlaceholder(project, document, placeholder, visibleLength, replacementText);
        }
      }
      else {
        if (minIndex > toSubtaskIndex && minIndex <= fromSubtaskIndex) {
          AnswerPlaceholderSubtaskInfo minInfo = placeholder.getSubtaskInfos().get(minIndex);
          if (minInfo.isNeedInsertText()) {
            EduUtils.replaceAnswerPlaceholder(project, document, placeholder, visibleLength, "");
          }
          else {
            String replacementText = minInfo.getPlaceholderText();
            EduUtils.replaceAnswerPlaceholder(project, document, placeholder, visibleLength, replacementText);
          }
        }
      }
    }
    taskFile.setTrackLengths(true);
  }
}