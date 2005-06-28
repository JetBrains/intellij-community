package com.intellij.openapi.wm.impl.status;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.impl.EmptyIcon;
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

public class TogglePopupHintsPanel extends JLabel {
  private static final Icon INSPECTIONS_ICON = IconLoader.getIcon("/general/toolWindowInspection.png");
  private static final Icon INSPECTIONS_OFF_ICON = IconLoader.getIcon("/general/inspectionsOff.png");
  private static final Icon EMPTY_ICON = EmptyIcon.create(INSPECTIONS_ICON.getIconWidth(), INSPECTIONS_ICON.getIconHeight());

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
    setIcon(EMPTY_ICON);
    setHorizontalAlignment(CENTER);
    setIconTextGap(0);
  }

  void update() {
    if (isStateChangeable()) {
      if (HighlightUtil.isRootInspected(getCurrentFile())) {
        setIcon(INSPECTIONS_ICON);
      } else {
        setIcon(INSPECTIONS_OFF_ICON);
      }
    }
    else {
      setIcon(EMPTY_ICON);
    }
  }

  private boolean isStateChangeable() {
    if (getCurrentFile() == null) {
      return false;
    }
    return getCurrentEditor() != null;
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

  @Nullable
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
