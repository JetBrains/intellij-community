package com.jetbrains.edu.learning.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.checker.StudyCheckListener;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.checker.StudyCheckUtils;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TheoryTask;
import com.jetbrains.edu.learning.editor.StudyEditor;
import icons.EducationalCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class StudyCheckAction extends StudyActionWithShortcut {
  public static final String SHORTCUT = "ctrl alt pressed ENTER";
  private static final String TEXT = "Check Task";

  protected final Ref<Boolean> myCheckInProgress = new Ref<>(false);

  public StudyCheckAction() {
    super(getTextWithShortcuts(TEXT),
          "Check current task", EducationalCoreIcons.CheckTask);
  }

  @NotNull
  private static String getTextWithShortcuts(String text) {
    return text + "(" + KeymapUtil.getShortcutText(new KeyboardShortcut(KeyStroke.getKeyStroke(SHORTCUT), null)) + ")";
  }

  public abstract void check(@NotNull final Project project);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    if (DumbService.isDumb(project)) {
      StudyCheckUtils
        .showTestResultPopUp("Checking is not available while indexing is in progress", MessageType.WARNING.getPopupBackground(), project);
      return;
    }
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
    check(project);
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
          presentation.setText(getTextWithShortcuts("Get Next Recommendation"));
        }
        else {
          presentation.setText(getTextWithShortcuts(TEXT));
        }
      }
    }
  }

  @Override
  public String[] getShortcuts() {
    return new String[]{SHORTCUT};
  }
}
