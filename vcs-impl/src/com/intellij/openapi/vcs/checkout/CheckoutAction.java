package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.CheckoutProvider;
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
    Project project = e.getData(PlatformDataKeys.PROJECT);
    project = (project == null) ? ProjectManager.getInstance().getDefaultProject() : project;
    myProvider.doCheckout(project, new MyListener(project));
  }

  private static VirtualFile refreshVFS(final File directory) {
    final Ref<VirtualFile> result = new Ref<VirtualFile>();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final LocalFileSystem lfs = LocalFileSystem.getInstance();
        final VirtualFile vDir = lfs.refreshAndFindFileByIoFile(directory);
        result.set(vDir);
        if (vDir != null) {
          final LocalFileSystem.WatchRequest watchRequest = lfs.addRootToWatch(vDir.getPath(), true);
          assert watchRequest != null;
          ((NewVirtualFile)vDir).markDirtyRecursively();
          vDir.refresh(false, true);
          lfs.removeWatchedRoot(watchRequest);
        }
      }
    });
    return result.get();
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
      if (!myFoundProject) {
        final VirtualFile virtualFile = refreshVFS(directory);
        if (virtualFile != null) {
          if (myFirstDirectory == null) {
            myFirstDirectory = directory;
          }
          notifyCheckoutListeners(directory, CheckoutListener.EP_NAME);
        }
      }
    }

    private void notifyCheckoutListeners(final File directory, final ExtensionPointName<CheckoutListener> epName) {
      CheckoutListener[] listeners = Extensions.getExtensions(epName);
      for(CheckoutListener listener: listeners) {
        myFoundProject = listener.processCheckedOutDirectory(myProject, directory);
        if (myFoundProject) break;
      }
    }

    public void checkoutCompleted() {
      if (!myFoundProject && myFirstDirectory != null) {
        notifyCheckoutListeners(myFirstDirectory, CheckoutListener.COMPLETED_EP_NAME);
      }
    }
  }
}
