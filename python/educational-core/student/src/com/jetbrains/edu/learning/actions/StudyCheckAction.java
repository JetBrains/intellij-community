package com.jetbrains.edu.learning.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Ref;
import com.jetbrains.edu.learning.StudyActionListener;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.checker.StudyCheckUtils;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class StudyCheckAction extends StudyActionWithShortcut {
  public static final String SHORTCUT = "ctrl alt pressed ENTER";

  protected Ref<Boolean> myCheckInProgress = new Ref<>(false);

  public StudyCheckAction() {
    super("Check Task (" + KeymapUtil.getShortcutText(new KeyboardShortcut(KeyStroke.getKeyStroke(SHORTCUT), null)) + ")", "Check current task", InteractiveLearningIcons.Resolve);
  }

  public abstract void check(@NotNull final Project project);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    if (DumbService.isDumb(project)) {
      StudyCheckUtils.showTestResultPopUp("Checking is not available while indexing is in progress", MessageType.WARNING.getPopupBackground(), project);
      return;
    }
    for (StudyActionListener listener : Extensions.getExtensions(StudyActionListener.EP_NAME)) {
      listener.beforeCheck(e);
    }
    check(project);
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    StudyUtils.updateAction(e);
    if (presentation.isEnabled()) {
      presentation.setEnabled(!myCheckInProgress.get());
    }
  }

  @Override
  public String[] getShortcuts() {
    return new String[] {SHORTCUT};
  }
}
