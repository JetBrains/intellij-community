package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.GotoLineNumberDialog;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

//Made public for Fabrique
public class PositionPanel extends TextPanel {
  public PositionPanel() {
    super(new String[]{"XXXXXXXXX"},false);

    addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          final Project project = (Project)DataManager.getInstance().getDataContext(PositionPanel.this).getData(DataConstants.PROJECT);
          if (project == null) return;
          final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
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
            "Go to Line",
            null
          );
        }
      }
    });
  }
}
