package com.intellij.openapi.wm.impl.status;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.PopupHandler;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

//Made public for Fabrique
public class TogglePopupHintsPanel extends TextPanel {
  private static final String STATE_ON_MSG = "Import Popup: ON";
  private static final String STATE_OFF_MSG = "Import Popup: OFF";

  public TogglePopupHintsPanel(ActionManager actionManager) {
    super(new String[]{STATE_ON_MSG, STATE_OFF_MSG}, false);
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new TurnOnAction());
    group.add(new TurnOffAction());
    PopupHandler.installUnknownPopupHandler(this, group, actionManager);

    addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2 && isStateChangeable()) {
          setHintsState(!getCurrentState());
        }
      }
    });
  }

  void update() {
    if (isStateChangeable()) {
      setText(getCurrentState() ? STATE_ON_MSG : STATE_OFF_MSG);
      setToolTipText("Double-click to toggle popup hints for this file");
    }
    else {
      setText("");
      setToolTipText(null);
    }
  }

  private boolean isStateChangeable() {
    final PsiFile file = getCurrentFile();
    if (file == null) {
      return false;
    }
    return DaemonCodeAnalyzer.getInstance(getCurrentProject()).isAutohintsAvailable(file);
  }

  private boolean getCurrentState() {
    if (!isStateChangeable()) {
      return false;
    }
    final PsiFile file = getCurrentFile();
    final Project project = getCurrentProject();
    return DaemonCodeAnalyzer.getInstance(project).isImportHintsEnabled(file);
  }

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

  private void setHintsState(final boolean isOn) {
    final PsiFile file = getCurrentFile();
    if (file == null) {
      return;
    }
    DaemonCodeAnalyzer.getInstance(getCurrentProject()).setImportHintsEnabled(file, isOn);
    update();
  }

  private final class TurnOnAction extends ToggleAction {
    public TurnOnAction() {
      super("Import Popup ON");
    }

    public boolean isSelected(final AnActionEvent event) {
      return getCurrentState();
    }

    public void setSelected(final AnActionEvent event,final boolean flag) {
      setHintsState(true);
    }

    public void update(final AnActionEvent e){
      super.update(e);
      e.getPresentation().setEnabled(isStateChangeable());
    }
  }

  private final class TurnOffAction extends ToggleAction {
    public TurnOffAction() {
      super("Import Popup OFF");
    }

    public boolean isSelected(final AnActionEvent event) {
      return !getCurrentState();
    }

    public void setSelected(final AnActionEvent event,final boolean flag) {
      setHintsState(false);
    }

    public void update(final AnActionEvent e){
      super.update(e);
      e.getPresentation().setEnabled(isStateChangeable());
    }
  }
}
