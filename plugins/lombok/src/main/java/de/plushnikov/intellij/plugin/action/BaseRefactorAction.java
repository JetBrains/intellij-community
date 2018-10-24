package de.plushnikov.intellij.plugin.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Date: 15.12.13 Time: 23:09
 */
public abstract class BaseRefactorAction extends AnAction {

  protected abstract BaseRefactorHandler initHandler(Project project, DataContext dataContext);

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    boolean visible = isActionAvailable(e);

    final Presentation presentation = e.getPresentation();
    presentation.setVisible(visible);
    presentation.setEnabled(visible);
  }

  private boolean isActionAvailable(AnActionEvent e) {
    final VirtualFile file = getVirtualFiles(e);
    if (getEventProject(e) != null && file != null) {
      final FileType fileType = file.getFileType();
      return StdFileTypes.JAVA.equals(fileType);
    }
    return false;
  }

  private VirtualFile getVirtualFiles(AnActionEvent e) {
    return PlatformDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    final BaseRefactorHandler handler = initHandler(project, e.getDataContext());

    boolean processChooser = handler.processChooser();

    if (processChooser) {
      final Editor editor = getEditor(e);

      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(handler);
        }
      }, getClass().getName() + "-Commandname", DocCommandGroupId.noneGroupId(editor.getDocument()));
    }
  }

  private Editor getEditor(AnActionEvent e) {
    return PlatformDataKeys.EDITOR.getData(e.getDataContext());
  }
}
