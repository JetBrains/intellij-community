package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.GotoLineNumberDialog;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.ui.StatusBarInformer;
import com.intellij.ui.UIBundle;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

//Made public for Fabrique
public class PositionPanel extends TextPanel {
  public PositionPanel() {
    super(new String[]{"#########"},false);

    addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          final Project project = getProject();
          if (project == null) return;
          final Editor editor = getEditor(project);
          if (editor == null) return;
          final CommandProcessor processor = CommandProcessor.getInstance();
          processor.executeCommand(
              project, new Runnable(){
              public void run() {
                final GotoLineNumberDialog dialog = new GotoLineNumberDialog(project, editor);
                dialog.show();
                IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
              }
            },
              UIBundle.message("go.to.line.command.name"),
            null
          );
        }
      }
    });

    new StatusBarInformer(this, null) {
      protected String getText() {
        final Editor editor = getEditor();
        return editor == null ? null : UIBundle.message("go.to.line.command.double.click"); 
      }
    };
  }

  private Editor getEditor() {
    final Project project = getProject();
    if (project == null) return null;
    return getEditor(project);
  }

  private Editor getEditor(final Project project) {
    return FileEditorManager.getInstance(project).getSelectedTextEditor();
  }

  private Project getProject() {
    return DataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(PositionPanel.this));
  }
}
