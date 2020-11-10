package de.plushnikov.intellij.plugin.action;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Date: 15.12.13 Time: 23:09
 */
public abstract class BaseRefactorAction extends AnAction {

  protected abstract BaseRefactorHandler initHandler(Project project, DataContext dataContext);

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);

    boolean visible = false;

    final VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
    if (getEventProject(event) != null && file != null) {
      final FileType fileType = file.getFileType();
      visible = JavaFileType.INSTANCE.equals(fileType);
    }

    event.getPresentation().setEnabledAndVisible(visible);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getProject();
    final BaseRefactorHandler handler = initHandler(project, event.getDataContext());

    boolean processChooser = handler.processChooser();

    if (processChooser) {
      final Editor editor = event.getData(CommonDataKeys.EDITOR);

      @NlsSafe String name = getClass().getName() + "-Commandname";
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(handler);
        }
      }, name, DocCommandGroupId.noneGroupId(editor.getDocument()));
    }
  }
}
