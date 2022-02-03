// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.formatter;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.LineSeparatorPanel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.sh.codeStyle.ShCodeStyleSettings;
import com.intellij.sh.psi.ShFile;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.wm.StatusBar.StandardWidgets.LINE_SEPARATOR_PANEL;

public class ShLineEndingsAdapter implements FileDocumentManagerListener {
  private boolean statusBarUpdated;

  @Override
  public void beforeAllDocumentsSaving() {
    statusBarUpdated = false;
  }

  @Override
  public void beforeDocumentSaving(@NotNull Document document) {
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null) return;

    Project project = ProjectUtil.guessProjectForFile(virtualFile);
    if (project == null) return;
    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
    if (!(psiFile instanceof ShFile)) return;

    CodeStyleSettings settings = CodeStyle.getSettings(psiFile);
    ShCodeStyleSettings shSettings = settings.getCustomSettings(ShCodeStyleSettings.class);
    String expectedLineSeparator = shSettings.USE_UNIX_LINE_SEPARATOR ? LineSeparator.LF.getSeparatorString() :
                                   LineSeparator.getSystemLineSeparator().getSeparatorString();
    String fileDetectedLineSeparator = virtualFile.getDetectedLineSeparator();
    if (fileDetectedLineSeparator == null || !fileDetectedLineSeparator.equals(expectedLineSeparator)) {
      virtualFile.setDetectedLineSeparator(expectedLineSeparator);
      if (!statusBarUpdated) {
        statusBarUpdated = true;
        updateStatusBar(project);
      }
    }
  }

  private static void updateStatusBar(@NotNull Project project) {
    ApplicationManager.getApplication().invokeLater(() -> {
      IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
      StatusBar statusBar = frame != null ? frame.getStatusBar() : null;
      StatusBarWidget widget = statusBar != null ? statusBar.getWidget(LINE_SEPARATOR_PANEL) : null;

      if (widget instanceof LineSeparatorPanel) {
        FileEditorManagerEvent event = new FileEditorManagerEvent(FileEditorManager.getInstance(project), null, null);
        ((LineSeparatorPanel)widget).selectionChanged(event);
      }
    });
  }
}
