package com.intellij.openapi.wm.impl.status;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.impl.EmptyIcon;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.HectorComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class TogglePopupHintsPanel extends JLabel {
  private static final Icon INSPECTIONS_ICON = IconLoader.getIcon("/objectBrowser/showGlobalInspections.png");
  private static final Icon INSPECTIONS_OFF_ICON = IconLoader.getIcon("/general/inspectionsOff.png");
  private static final Icon EMPTY_ICON = EmptyIcon.create(INSPECTIONS_ICON.getIconWidth(), INSPECTIONS_ICON.getIconHeight());

  private ProjectManagerListener myProjectManagerListener = new MyProjectManagerListener();
  private MyFileEditorManagerListener myFileEditorManagerListener = new MyFileEditorManagerListener();

  public TogglePopupHintsPanel() {
    super(EMPTY_ICON);
    addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        Point point = new Point(0, 0);
        final Editor editor = getCurrentEditor();
        final PsiFile file = getCurrentFile();
        if (editor != null && file != null) {
          if (!DaemonCodeAnalyzer.getInstance(file.getProject()).isHighlightingAvailable(file)) return;
          point = SwingUtilities.convertPoint(TogglePopupHintsPanel.this, point, editor.getComponent().getRootPane().getLayeredPane());
          final HectorComponent component = new HectorComponent(file);
          final Dimension dimension = component.getPreferredSize();
          point = new Point(point.x - dimension.width, point.y - dimension.height);
          component.showComponent(editor, point);
        }
      }
    });
    setIconTextGap(0);
    ProjectManager.getInstance().addProjectManagerListener(myProjectManagerListener);
    //not to miss already opened projects in first frame if any
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : openProjects) {
      FileEditorManager.getInstance(project).addFileEditorManagerListener(myFileEditorManagerListener);
    }
  }

  void updateStatus(boolean isClear){
    updateStatus(isClear, getCurrentFile());
  }

  void updateStatus(boolean isClear, PsiFile file) {
    if (isClear){
      setIcon(EMPTY_ICON);
      setToolTipText(null);
      return;
    }
    if (isStateChangeable(file)) {
      if (HighlightUtil.isRootInspected(file)) {
        setIcon(INSPECTIONS_ICON);
      } else {
        setIcon(INSPECTIONS_OFF_ICON);
      }
      setToolTipText(UIBundle.message("popup.hints.panel.click.to.configure.highlighting.tooltip.text"));
    }
    else {
      setIcon(EMPTY_ICON);
      setToolTipText(null);
    }
  }

  private boolean isStateChangeable(PsiFile file) {
    if (file == null) {
      return false;
    }
    if (!DaemonCodeAnalyzer.getInstance(file.getProject()).isHighlightingAvailable(file)) return false;
    return getCurrentEditor() != null;
  }

  @Nullable
  private PsiFile getCurrentFile() {
    final Project project = getCurrentProject();
    if (project == null) {
      return null;
    }

    final Editor selectedTextEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (selectedTextEditor == null){
      return null;
    }
    final Document document = selectedTextEditor.getDocument();

    return PsiDocumentManager.getInstance(project).getPsiFile(document);
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

  public Point getToolTipLocation(MouseEvent event) {
    return new Point(0, -20);
  }

  public void dispose() {
    ProjectManager.getInstance().removeProjectManagerListener(myProjectManagerListener);
  }

  private final class MyFileEditorManagerListener extends FileEditorManagerAdapter {
    public void selectionChanged(FileEditorManagerEvent e){
      final Project project = getCurrentProject();
      if (project != null){
        final VirtualFile vFile = e.getNewFile();
        if (vFile != null) {
          updateStatus(false, PsiManager.getInstance(project).findFile(vFile));
        } else {
          updateStatus(true, null);
        }
      }
    }
  }

  private final class MyProjectManagerListener implements ProjectManagerListener{

    public void projectOpened(Project project) {
      FileEditorManager.getInstance(project).addFileEditorManagerListener(myFileEditorManagerListener);
    }

    public boolean canCloseProject(Project project) {
      return true;
    }

    public void projectClosed(Project project) {
      FileEditorManager.getInstance(project).removeFileEditorManagerListener(myFileEditorManagerListener);
    }

    public void projectClosing(Project project) {
    }
  }
}
