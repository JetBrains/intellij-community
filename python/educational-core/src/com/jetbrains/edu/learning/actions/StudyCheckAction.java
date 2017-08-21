package com.jetbrains.edu.learning.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudySettings;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.checker.StudyCheckListener;
import com.jetbrains.edu.learning.checker.StudyCheckResult;
import com.jetbrains.edu.learning.checker.StudyCheckUtils;
import com.jetbrains.edu.learning.checker.StudyTaskChecker;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TheoryTask;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import icons.EducationalCoreIcons;
import org.jetbrains.annotations.NotNull;

public class StudyCheckAction extends StudyActionWithShortcut {
  public static final String SHORTCUT = "ctrl alt pressed ENTER";
  public static final String ACTION_ID = "Edu.Check";
  private static final String TEXT = "Check Task";
  public static final String FAILED_CHECK_LAUNCH = "Failed to launch checking";

  protected final Ref<Boolean> myCheckInProgress = new Ref<>(false);

  public StudyCheckAction() {
    super(TEXT,"Check current task", EducationalCoreIcons.CheckTask);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    EduUsagesCollector.taskChecked();
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    if (DumbService.isDumb(project)) {
      StudyCheckUtils
        .showTestResultPopUp("Checking is not available while indexing is in progress", MessageType.WARNING.getPopupBackground(), project);
      return;
    }
    StudyCheckUtils.hideTestResultsToolWindow(project);
    FileDocumentManager.getInstance().saveAllDocuments();
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor == null) {
      return;
    }
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
    if (virtualFile == null) {
      return;
    }
    Task task = StudyUtils.getTaskForFile(project, virtualFile);
    if (task == null) {
      return;
    }
    for (StudyCheckListener listener : Extensions.getExtensions(StudyCheckListener.EP_NAME)) {
      listener.beforeCheck(project, task);
    }
    ProgressManager.getInstance().run(new StudyCheckTask(project, task));
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    StudyUtils.updateAction(e);
    if (presentation.isEnabled()) {
      updateDescription(e);
      presentation.setEnabled(!myCheckInProgress.get());
    }
  }

  private static void updateDescription(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final Project project = e.getProject();
    if (project != null) {
      final StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
      if (studyEditor != null) {
        final Task task = studyEditor.getTaskFile().getTask();
        if (task instanceof TheoryTask) {
          presentation.setText(task.getLesson().getCourse().isAdaptive() ? "Get Next Recommendation" : "Mark as read");
        }
        else {
          presentation.setText(TEXT);
        }
      }
    }
  }

  @NotNull
  @Override
  public String getActionId() {
    return ACTION_ID;
  }

  @Override
  public String[] getShortcuts() {
    return new String[]{SHORTCUT};
  }

  private class StudyCheckTask extends com.intellij.openapi.progress.Task.Backgroundable {
    private final Project myProject;
    private final Task myTask;
    private final StudyTaskChecker myChecker;
    private StudyCheckResult myResult;

    public StudyCheckTask(@NotNull Project project, @NotNull Task task) {
      super(project, "Checking Task", true, PerformInBackgroundOption.DEAF);
      myProject = project;
      myTask = task;
      myChecker = task.getChecker(myProject);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      myCheckInProgress.set(true);
      boolean isRemote = myTask.getLesson().getCourse() instanceof RemoteCourse;
      myResult = isRemote ? checkOnRemote() : myChecker.check();
    }

    @Override
    public void onSuccess() {
      String message = myResult.getMessage();
      StudyStatus status = myResult.getStatus();
      myTask.setStatus(status);
      switch (status) {
        case Failed:
          myChecker.onTaskFailed(message);
          break;
        case Solved:
          myChecker.onTaskSolved(message);
          break;
        default:
          StudyCheckUtils.showTestResultPopUp(message, MessageType.WARNING.getPopupBackground(), myProject);
      }
      ApplicationManager.getApplication().invokeLater(() -> {
        StudyUtils.updateToolWindows(myProject);
        ProjectView.getInstance(myProject).refresh();

        for (StudyCheckListener listener : StudyCheckListener.EP_NAME.getExtensions()) {
          listener.afterCheck(myProject, myTask);
        }
      });
      myChecker.clearState();
      myCheckInProgress.set(false);
    }

    @Override
    public void onCancel() {
      myChecker.clearState();
      myCheckInProgress.set(false);
    }

    private StudyCheckResult checkOnRemote() {
      return myChecker.checkOnRemote(StudySettings.getInstance().getUser());
    }
  }
}
