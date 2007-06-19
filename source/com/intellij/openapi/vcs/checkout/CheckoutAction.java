package com.intellij.openapi.vcs.checkout;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;

import java.io.File;

public class CheckoutAction extends AnAction {
  private final CheckoutProvider myProvider;

  public CheckoutAction(final CheckoutProvider provider) {
    myProvider = provider;
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    myProvider.doCheckout(new MyListener(project));
  }

  private static void refreshVFS(final File directory) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final LocalFileSystem lfs = LocalFileSystem.getInstance();
        final VirtualFile vDir = lfs.refreshAndFindFileByIoFile(directory);
        assert vDir != null;
        final LocalFileSystem.WatchRequest watchRequest = lfs.addRootToWatch(vDir.getPath(), true);
        assert watchRequest != null;
        ((NewVirtualFile)vDir).markDirtyRecursively();
        vDir.refresh(false, true);
        lfs.removeWatchedRoot(watchRequest);
      }
    });
  }

  private static void processNoProject(final Project project, final File directory) {
    int rc = Messages.showYesNoDialog(project, VcsBundle.message("checkout.create.project.prompt", directory.getAbsolutePath()),
                                      VcsBundle.message("checkout.title"), Messages.getQuestionIcon());
    if (rc == 0) {
      ProjectUtil.createNewProject(project, directory.getAbsolutePath());
    }
  }

  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setText(myProvider.getVcsName(), true);
  }

  private static class MyListener implements CheckoutProvider.Listener {
    private final Project myProject;
    private boolean myFoundProject = false;
    private File myFirstDirectory;

    public MyListener(final Project project) {
      myProject = project;
    }

    public void directoryCheckedOut(final File directory) {
      if (myFirstDirectory == null) {
        myFirstDirectory = directory;
      }
      if (!myFoundProject) {
        refreshVFS(directory);
        CheckoutListener[] listeners = Extensions.getExtensions(CheckoutListener.EP_NAME);
        for(CheckoutListener listener: listeners) {
          myFoundProject = listener.processCheckedOutDirectory(myProject, directory);
          if (myFoundProject) break;
        }
      }
    }

    public void checkoutCompleted() {
      if (!myFoundProject && myFirstDirectory != null) {
        processNoProject(myProject, myFirstDirectory);
      }
    }
  }
}
