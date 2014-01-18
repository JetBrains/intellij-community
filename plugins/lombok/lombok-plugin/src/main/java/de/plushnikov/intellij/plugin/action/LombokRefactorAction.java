package de.plushnikov.intellij.plugin.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.project.Project;

/**
 * Date: 15.12.13 Time: 23:09
 */
public abstract class LombokRefactorAction extends AnAction {

  protected abstract LombokRefactorHandler initHandler(Project project, DataContext dataContext);

  public void actionPerformed(AnActionEvent e) {
    final LombokRefactorHandler handler = initHandler(e.getProject(), e.getDataContext());

    boolean processChooser = handler.processChooser();

    if (processChooser) {
      final Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());

      CommandProcessor.getInstance().executeCommand(e.getProject(), new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(handler);
        }
      }, getClass().getName() + "-Commandname", DocCommandGroupId.noneGroupId(editor.getDocument()));
    }
  }
}
