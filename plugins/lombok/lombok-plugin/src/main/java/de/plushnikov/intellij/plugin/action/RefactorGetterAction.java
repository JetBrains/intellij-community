package de.plushnikov.intellij.plugin.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.project.Project;

public class RefactorGetterAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {

    final Project project = e.getProject();
    final DataContext dataContext = e.getDataContext();

    final RefactorGetterHandler handler = new RefactorGetterHandler(project, dataContext);

    boolean processChooser = handler.processChooser();

    if (processChooser) {
      final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);

      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(handler);
        }
      }, "@Getter-Commandname", DocCommandGroupId.noneGroupId(editor.getDocument()));
    }
  }
}