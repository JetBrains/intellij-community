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
import com.intellij.openapi.wm.StatusBar;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import com.intellij.ui.UIBundle;
import com.intellij.ui.StatusBarInformer;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;

public class ToggleReadOnlyAttributePanel extends JLabel {
  public ToggleReadOnlyAttributePanel(StatusBar status) {
    addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          processDoubleClick();
        }
      }
    });
    setIconTextGap(0);
    new StatusBarInformer(this, null, status) {

      protected String getText() {
        final FileEditorManager editor = getEditor();
        if (editor == null) return null;
        return isReadOnlyApplicable(editor.getSelectedFiles())
               ? UIBundle.message("read.only.attr.panel.double.click.to.toggle.attr.tooltip.text") : null;
      }
    };
  }

  private void processDoubleClick() {
    final Project project = getProject();
    if (project == null) {
      return;
    }
    final FileEditorManager editorManager = getEditor(project);
    final VirtualFile[] files = editorManager.getSelectedFiles();
    if (!isReadOnlyApplicable(files)) {
      return;
    }
    FileDocumentManager.getInstance().saveAllDocuments();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          ReadOnlyAttributeUtil.setReadOnlyAttribute(files[0], files[0].isWritable());
        }
        catch (IOException e) {
          Messages.showMessageDialog(project, e.getMessage(), UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
        }
      }
    });
  }

  private boolean isReadOnlyApplicable(final VirtualFile[] files) {
    return files.length > 0 && !files[0].getFileSystem().isReadOnly();
  }

  private FileEditorManager getEditor() {
    final Project project = getProject();
    if (project == null) return null;
    return FileEditorManager.getInstance(project);
  }

  private FileEditorManager getEditor(final Project project) {
    return FileEditorManager.getInstance(project);
  }

  private Project getProject() {
    return (Project)DataManager.getInstance().getDataContext(this).getData(DataConstants.PROJECT);
  }
}
