package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ReadOnlyAttributeUtil;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;

//Made public for Fabrique
public class ToggleReadOnlyAttributePanel extends JLabel {
  public ToggleReadOnlyAttributePanel() {
    setToolTipText("Double-click to toggle the read-only attribute");
    addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          processDoubleClick();
        }
      }
    });
    setIconTextGap(0);
  }

  private void processDoubleClick() {
    final Project project = (Project)DataManager.getInstance().getDataContext(this).getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    final FileEditorManager editorManager = FileEditorManager.getInstance(project);
    final VirtualFile[] files = editorManager.getSelectedFiles();
    if (files.length == 0 || !(files[0].getFileSystem() instanceof LocalFileSystem)) {
      return;
    }
    FileDocumentManager.getInstance().saveAllDocuments();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          ReadOnlyAttributeUtil.setReadOnlyAttribute(files[0], files[0].isWritable());
        }
        catch (IOException e) {
          Messages.showMessageDialog(project, e.getMessage(), "Error", Messages.getErrorIcon());
        }
      }
    });
  }
}
