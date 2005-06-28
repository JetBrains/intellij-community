package com.intellij.openapi.wm.impl.status;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorMarkupHintComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class TogglePopupHintsPanel extends JButton {
  private static final Icon INSPECTIONS_ICON = IconLoader.getIcon("/general/toolWindowInspection.png");
  private static final Icon HIGHLIGHTING_ICON = IconLoader.getIcon("/general/toolWindowInspection.png");
  private static final Icon DISABLED_ICON = IconLoader.getIcon("/general/toolWindowInspection.png");
  public TogglePopupHintsPanel() {
    addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        Point point = new Point(0, 0);
        final Editor editor = getCurrentEditor();
        final PsiFile file = getCurrentFile();
        if (editor != null && file != null) {
          point = SwingUtilities.convertPoint(TogglePopupHintsPanel.this, point, editor.getComponent().getRootPane().getLayeredPane());
          final EditorMarkupHintComponent component = new EditorMarkupHintComponent(file);
          final Dimension dimension = component.getPreferredSize();
          point = new Point(point.x - dimension.width, point.y - dimension.height);
          component.showComponent(editor, point);
        }
      }
    });
    setIcon(null);
    setBorder(null);
  }

  void update() {
    if (isStateChangeable()) {
      if (HighlightUtil.isRootInspected(getCurrentFile())) {
        setIcon(INSPECTIONS_ICON);
      } else {
        setIcon(HIGHLIGHTING_ICON);
      }
    }
    else {
      setIcon(DISABLED_ICON);
    }
  }

  private boolean isStateChangeable() {
    if (getCurrentFile() == null) {
      return false;
    }
    if (getCurrentEditor() == null) {
      return false;
    }
    return true;
  }

  @Nullable
  private PsiFile getCurrentFile() {
    final Project project = getCurrentProject();
    if (project == null) {
      return null;
    }

    final VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
    if (files.length == 0 || !files[0].isValid()) {
      return null;
    }

    final PsiFile file = PsiManager.getInstance(project).findFile(files[0]);
    return file;
  }

  private Project getCurrentProject() {
    return (Project)DataManager.getInstance().getDataContext(this).getData(DataConstants.PROJECT);
  }

  @Nullable
  private Editor getCurrentEditor() {
    final Project project = getCurrentProject();
    if (project == null) {
      return null;
    }
    return FileEditorManager.getInstance(project).getSelectedTextEditor();
  }

}
