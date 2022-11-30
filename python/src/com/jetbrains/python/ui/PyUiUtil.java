// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ui;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiManager;
import com.intellij.ui.awt.RelativePoint;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Assorted UI-related utility methods for Python.
 *
 * @see com.jetbrains.python.psi.PyUtil for Python code insight utilities.
 */
public class PyUiUtil {
  /**
   * Shows an information balloon in a reasonable place at the top right of the window.
   *
   * @param project     our project
   * @param message     the text, HTML markup allowed
   * @param messageType message type, changes the icon and the background.
   */
  public static void showBalloon(@NotNull Project project, @NotNull @PopupContent String message, @NotNull MessageType messageType) {
    // ripped from com.intellij.openapi.vcs.changes.ui.ChangesViewBalloonProblemNotifier
    final JFrame frame = WindowManager.getInstance().getFrame(project.isDefault() ? null : project);
    if (frame == null) return;
    final JComponent component = frame.getRootPane();
    if (component == null) return;
    final Rectangle rect = component.getVisibleRect();
    final Point p = new Point(rect.x + rect.width - 10, rect.y + 10);
    final RelativePoint point = new RelativePoint(component, p);

    JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, messageType.getDefaultIcon(), messageType.getPopupBackground(), null)
                  .setShowCallout(false).setCloseButtonEnabled(true)
                  .createBalloon().show(point, Balloon.Position.atLeft);
  }

  /**
   * Force re-highlighting in all open editors that belong to specified project.
   */
  public static void rehighlightOpenEditors(final @NotNull Project project) {
    ApplicationManager.getApplication().runWriteAction(() -> {

      for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
        if (editor instanceof EditorEx && editor.getProject() == project) {
          final VirtualFile vFile = editor.getVirtualFile();
          if (vFile != null) {
            final EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, vFile);
            ((EditorEx)editor).setHighlighter(highlighter);
          }
        }
      }
    });
  }

  public static void clearFileLevelInspectionResults(@NotNull Project project) {
    final DaemonCodeAnalyzerEx codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project);
    final PsiManager psiManager = PsiManager.getInstance(project);
    StreamEx.of(FileEditorManager.getInstance(project).getAllEditors())
      .map(editor -> editor.getFile())
      .nonNull()
      .map(file -> ReadAction.compute(() -> psiManager.findFile(file)))
      .nonNull()
      .forEach(file -> {
        codeAnalyzer.cleanFileLevelHighlights(Pass.LOCAL_INSPECTIONS, file);
      });
  }
}
