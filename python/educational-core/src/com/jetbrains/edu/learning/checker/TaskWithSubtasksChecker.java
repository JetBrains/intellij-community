package com.jetbrains.edu.learning.checker;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.StudySubtaskUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.PyCharmTask;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import com.jetbrains.edu.learning.stepic.StepicUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class TaskWithSubtasksChecker extends StudyTaskChecker<TaskWithSubtasks> {
  private StudyTaskChecker<PyCharmTask> myPyCharmTaskChecker;
  public TaskWithSubtasksChecker(@NotNull TaskWithSubtasks task,
                                 @NotNull Project project) {
    super(task, project);
    EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(myTask.getLesson().getCourse().getLanguageById());
    if (configurator != null) {
      myPyCharmTaskChecker  = configurator.getPyCharmTaskChecker(task, project);
    }
  }

  @Override
  public boolean validateEnvironment() {
    return myPyCharmTaskChecker.validateEnvironment();
  }

  @Override
  public StudyCheckResult check() {
    if (myPyCharmTaskChecker != null) {
      return myPyCharmTaskChecker.check();
    }
    return super.check();
  }

  @Override
  public StudyCheckResult checkOnRemote(@Nullable StepicUser user) {
    if (myPyCharmTaskChecker != null) {
      return myPyCharmTaskChecker.checkOnRemote(user);
    }
    return super.checkOnRemote(user);
  }

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
}
