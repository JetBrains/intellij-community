package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class CleanupWorker {
  private VirtualFile[] myRoots;
  private Project myProject;
  private String myTitleKey;

  public CleanupWorker(final VirtualFile[] roots, final Project project, final String titleKey) {
    myRoots = roots;
    myProject = project;
    myTitleKey = titleKey;
  }

  public void execute() {
    ApplicationManager.getApplication().saveAll();

    final List<Pair<SVNException, VirtualFile>> exceptions = new LinkedList<Pair<SVNException, VirtualFile>>();
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final SVNWCClient wcClient = vcs.createWCClient();

    final Task.Backgroundable task = new Task.Backgroundable(myProject, SvnBundle.message(myTitleKey), false, PerformInBackgroundOption.DEAF) {
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        VirtualFile currentRoot;
        for (VirtualFile root : myRoots) {
          currentRoot = root;
          try {
            final String path = root.getPath();
            indicator.setText(SvnBundle.message("action.Subversion.cleanup.progress.text", path));
            wcClient.doCleanup(new File(path));
          }
          catch (SVNException ex) {
            exceptions.add(new Pair<SVNException, VirtualFile>(ex, currentRoot));
          }
        }
      }

      @Override
      public void onCancel() {
        onSuccess();
      }

      @Override
      public void onSuccess() {
        if (myProject.isDisposed()) {
          return;
        }
        final VcsDirtyScopeManager manager = VcsDirtyScopeManager.getInstance(myProject);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                if (myProject.isDisposed()) {
                  return;
                }
                for (final VirtualFile root : myRoots) {
                  root.refresh(false, true);
                }
              }
            });
          }
        });
        for (final VirtualFile root : myRoots) {
          manager.fileDirty(root);
        }

        if (! exceptions.isEmpty()) {
          final List<VcsException> vcsExceptions = new LinkedList<VcsException>();
          for (Pair<SVNException, VirtualFile> pair : exceptions) {
            final SVNException exception = pair.first;
            vcsExceptions.add(new VcsException(SvnBundle.message("action.Subversion.cleanup.error.message",
                                                              FileUtil.toSystemDependentName(pair.second.getPath()),
                                                              ((exception == null) ? "" : exception.getMessage()))));
          }
          final AbstractVcsHelper helper = AbstractVcsHelper.getInstance(myProject);
          helper.showErrors(vcsExceptions, SvnBundle.message(myTitleKey));
        }
      }
    };

    ProgressManager.getInstance().run(task);
  }
}
