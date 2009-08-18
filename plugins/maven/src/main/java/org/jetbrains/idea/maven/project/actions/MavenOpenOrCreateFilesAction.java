package org.jetbrains.idea.maven.project.actions;

import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.NotificationsManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public abstract class MavenOpenOrCreateFilesAction extends MavenAction {
  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    List<File> files = getFiles(e);
    assert !files.isEmpty();

    List<VirtualFile> virtualFiles = collectVirtualFiles(files);

    String text;
    boolean enabled = true;

    if (files.size() == 1 && virtualFiles.isEmpty()) {
      text = "Create ''{0}''";
    }
    else {
      enabled = virtualFiles.size() == files.size();
      text = "Open ''{0}''";
    }

    Presentation p = e.getPresentation();
    p.setText(MessageFormat.format(text, files.get(0).getName()));
    p.setEnabled(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = MavenActionUtil.getProject(e);
    final List<File> files = getFiles(e);
    final List<VirtualFile> virtualFiles = collectVirtualFiles(files);

    if (files.size() == 1 && virtualFiles.isEmpty()) {
      new WriteCommandAction(project, e.getPresentation().getText()) {
        @Override
        protected void run(Result result) throws Throwable {
          File file = files.get(0);
          try {
            VirtualFile newFile = VfsUtil.createDirectoryIfMissing(file.getParent()).createChildData(this, file.getName());
            virtualFiles.add(newFile);
            MavenUtil.runFileTemplate(project, newFile, getFileTemplate(), true);
          }
          catch (IOException ex) {
            NotificationsManager.getNotificationsManager()
              .notify("Cannot create " + file, ex.getMessage(), NotificationType.ERROR, NotificationListener.REMOVE);
          }
        }
      }.execute();
      return;
    }

    for (VirtualFile each : virtualFiles) {
      new OpenFileDescriptor(project, each).navigate(true);
    }
  }

  private List<VirtualFile> collectVirtualFiles(List<File> files) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (File each : files) {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(each);
      if (virtualFile != null) result.add(virtualFile);
    }
    return result;
  }

  protected abstract List<File> getFiles(AnActionEvent e);

  protected abstract String getFileTemplate();
}
