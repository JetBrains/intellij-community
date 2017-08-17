package com.jetbrains.edu.learning.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.problems.WolfTheProblemSolver;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudySubtaskUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduAnswerPlaceholderPainter;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.ChoiceTask;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import com.jetbrains.edu.learning.editor.StudyChoiceVariantsPanel;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import icons.EducationalCoreIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class StudyRefreshTaskFileAction extends StudyActionWithShortcut {
  public static final String ACTION_ID = "RefreshTaskAction";
  public static final String SHORTCUT = "ctrl shift pressed X";
  private static final Logger LOG = Logger.getInstance(StudyRefreshTaskFileAction.class.getName());

  public StudyRefreshTaskFileAction() {
    super("Reset Task File", "Refresh current task", EducationalCoreIcons.ResetTaskFile);
  }

  public static void refresh(@NotNull final Project project) {
    ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
      StudyState studyState = new StudyState(studyEditor);
      if (studyEditor == null || !studyState.isValid()) {
        LOG.info("RefreshTaskFileAction was invoked outside of Study Editor");
        return;
      }
      refreshFile(studyState, project);
      EduUsagesCollector.fileRefreshed();
    }));
  }

  private static void refreshFile(@NotNull final StudyState studyState, @NotNull final Project project) {
    final Editor editor = studyState.getEditor();
    final TaskFile taskFile = studyState.getTaskFile();
    final Task task = taskFile.getTask();
    if (task instanceof TaskWithSubtasks) {
      for (AnswerPlaceholder placeholder : taskFile.getActivePlaceholders()) {
        StudySubtaskUtils.refreshPlaceholder(editor, placeholder);
      }
    }
    else {
      if (!resetTaskFile(editor.getDocument(), project, taskFile)) {
        Messages.showInfoMessage("The initial text of task file is unavailable", "Failed to Refresh Task File");
        return;
      }
      if (task instanceof ChoiceTask) {
        final StudyToolWindow window = StudyUtils.getStudyToolWindow(project);
        if (window != null) {
          window.setBottomComponent(new StudyChoiceVariantsPanel((ChoiceTask)task));
        }
      }
    }
    WolfTheProblemSolver.getInstance(project).clearProblems(studyState.getVirtualFile());
    taskFile.setHighlightErrors(false);
    StudyUtils.drawAllAnswerPlaceholders(editor, taskFile);
    EduAnswerPlaceholderPainter.createGuardedBlocks(editor, taskFile);
    ApplicationManager.getApplication().invokeLater(
      () -> IdeFocusManager.getInstance(project).requestFocus(editor.getContentComponent(), true));

    StudyNavigator.navigateToFirstAnswerPlaceholder(editor, taskFile);
    showBalloon(project, MessageType.INFO);
  }

  private static boolean resetTaskFile(@NotNull final Document document,
                                       @NotNull final Project project,
                                       TaskFile taskFile) {
    resetDocument(document, taskFile);
    final Task task = taskFile.getTask();
    task.setStatus(StudyStatus.Unchecked);
    if (task instanceof ChoiceTask) {
      ((ChoiceTask)task).setSelectedVariants(new ArrayList<>());
    }
    resetAnswerPlaceholders(taskFile, project);
    ProjectView.getInstance(project).refresh();
    StudyUtils.updateToolWindows(project);
    return true;
  }

  private static void showBalloon(@NotNull final Project project, @NotNull final MessageType messageType) {
    BalloonBuilder balloonBuilder =
      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder("You can start again now", messageType, null);
    final Balloon balloon = balloonBuilder.createBalloon();
    StudyEditor selectedStudyEditor = StudyUtils.getSelectedStudyEditor(project);
    assert selectedStudyEditor != null;
    balloon.show(StudyUtils.computeLocation(selectedStudyEditor.getEditor()), Balloon.Position.above);
    Disposer.register(project, balloon);
  }

  private static void resetAnswerPlaceholders(TaskFile selectedTaskFile, Project project) {
    final StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    for (AnswerPlaceholder answerPlaceholder : selectedTaskFile.getActivePlaceholders()) {
      answerPlaceholder.reset();
      taskManager.setStatus(answerPlaceholder, StudyStatus.Unchecked);
    }
  }


  private static void resetDocument(@NotNull final Document document,
                                       @NotNull final TaskFile taskFile) {
    StudyUtils.deleteGuardedBlocks(document);
    taskFile.setTrackChanges(false);
    clearDocument(document);

    document.setText(taskFile.text);
    taskFile.setTrackChanges(true);
  }

  private static void clearDocument(@NotNull final Document document) {
    final int lineCount = document.getLineCount();
    if (lineCount != 0) {
      CommandProcessor.getInstance().runUndoTransparentAction(() -> document.deleteString(0, document.getLineEndOffset(lineCount - 1)));
    }
  }

  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getProject();
    if (project != null) {
      refresh(project);
    }
  }

  @Override
  public void update(AnActionEvent event) {
    StudyUtils.updateAction(event);
    final Project project = event.getProject();
    if (project != null) {
      StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
      StudyState studyState = new StudyState(studyEditor);
      Presentation presentation = event.getPresentation();
      if (!studyState.isValid()) {
        presentation.setEnabled(false);
        return;
      }

      Course course = StudyTaskManager.getInstance(project).getCourse();
      if (course == null) {
        return;
      }
      if (!EduNames.STUDY.equals(course.getCourseMode())) {
        presentation.setVisible(true);
        presentation.setEnabled(false);
      }
    }
  }

  @NotNull
  @Override
  public String getActionId() {
    return ACTION_ID;
  }

  @Nullable
  @Override
  public String[] getShortcuts() {
    return new String[]{SHORTCUT};
  }
}
