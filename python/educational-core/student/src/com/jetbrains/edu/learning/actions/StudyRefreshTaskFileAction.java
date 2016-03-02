package com.jetbrains.edu.learning.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.problems.WolfTheProblemSolver;
import com.jetbrains.edu.EduAnswerPlaceholderPainter;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.StudyStatus;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StudyRefreshTaskFileAction extends DumbAwareAction {
  public static final String ACTION_ID = "RefreshTaskAction";
  public static final String SHORTCUT = "ctrl shift pressed X";
  private static final Logger LOG = Logger.getInstance(StudyRefreshTaskFileAction.class.getName());

  public StudyRefreshTaskFileAction() {
    super("Reset Task File (" + KeymapUtil.getShortcutText(new KeyboardShortcut(KeyStroke.getKeyStroke(SHORTCUT), null)) + ")", "Refresh current task", AllIcons.Actions.Refresh);
  }

  public static void refresh(@NotNull final Project project) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
          @Override
          public void run() {
            StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
            StudyState studyState = new StudyState(studyEditor);
            if (studyEditor == null || !studyState.isValid()) {
              LOG.info("RefreshTaskFileAction was invoked outside of Study Editor");
              return;
            }
            refreshFile(studyState, project);
          }
        });
      }
    });
  }

  private static void refreshFile(@NotNull final StudyState studyState, @NotNull final Project project) {
    final Editor editor = studyState.getEditor();
    final TaskFile taskFile = studyState.getTaskFile();
    if (!resetTaskFile(editor.getDocument(), project, taskFile, studyState.getVirtualFile().getName())) {
      Messages.showInfoMessage("The initial text of task file is unavailable", "Failed to Refresh Task File");
      return;
    }
    WolfTheProblemSolver.getInstance(project).clearProblems(studyState.getVirtualFile());
    taskFile.setHighlightErrors(false);
    StudyUtils.drawAllWindows(editor, taskFile);
    EduAnswerPlaceholderPainter.createGuardedBlocks(editor, taskFile, true);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        IdeFocusManager.getInstance(project).requestFocus(editor.getContentComponent(), true);
      }
    });

    StudyNavigator.navigateToFirstAnswerPlaceholder(editor, taskFile);
    showBalloon(project, "You can start again now", MessageType.INFO);
  }

  private static boolean resetTaskFile(@NotNull final Document document,
                                       @NotNull final Project project,
                                       TaskFile taskFile,
                                       String name) {
    if (!resetDocument(document, taskFile, name)) {
      return false;
    }
    resetAnswerPlaceholders(taskFile, project);
    ProjectView.getInstance(project).refresh();
    StudyUtils.updateToolWindows(project);
    return true;
  }

  private static void showBalloon(@NotNull final Project project, String text, @NotNull final MessageType messageType) {
    BalloonBuilder balloonBuilder =
      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(text, messageType, null);
    final Balloon balloon = balloonBuilder.createBalloon();
    StudyEditor selectedStudyEditor = StudyUtils.getSelectedStudyEditor(project);
    assert selectedStudyEditor != null;
    balloon.show(StudyUtils.computeLocation(selectedStudyEditor.getEditor()), Balloon.Position.above);
    Disposer.register(project, balloon);
  }

  private static void resetAnswerPlaceholders(TaskFile selectedTaskFile, Project project) {
    final StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    for (AnswerPlaceholder answerPlaceholder : selectedTaskFile.getAnswerPlaceholders()) {
      answerPlaceholder.reset();
      taskManager.setStatus(answerPlaceholder, StudyStatus.Unchecked);
    }
  }


  private static boolean resetDocument(@NotNull final Document document,
                                       @NotNull final TaskFile taskFile,
                                       String fileName) {
    final Document patternDocument = StudyUtils.getPatternDocument(taskFile, fileName);
    if (patternDocument == null) {
      return false;
    }
    StudyUtils.deleteGuardedBlocks(document);
    taskFile.setTrackChanges(false);
    clearDocument(document);
    document.setText(patternDocument.getCharsSequence());
    taskFile.setTrackChanges(true);
    return true;
  }

  private static void clearDocument(@NotNull final Document document) {
    final int lineCount = document.getLineCount();
    if (lineCount != 0) {
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        @Override
        public void run() {
          document.deleteString(0, document.getLineEndOffset(lineCount - 1));
        }
      });
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
      if (!studyState.isValid()) {
        event.getPresentation().setEnabled(false);
      }
    }
  }
}
