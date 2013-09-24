/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.checkout;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.actions.ExclusiveBackgroundVcsAction;
import org.jetbrains.idea.svn.actions.SvnExcludingIgnoredOperation;
import org.jetbrains.idea.svn.checkin.IdeaCommitHandler;
import org.jetbrains.idea.svn.dialogs.CheckoutDialog;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.swing.*;
import java.io.File;

public class SvnCheckoutProvider implements CheckoutProvider {

  public void doCheckout(@NotNull final Project project, Listener listener) {
    CheckoutDialog dialog = new CheckoutDialog(project, listener);
    dialog.show();
  }

  public static void doCheckout(final Project project, final File target, final String url, final SVNRevision revision,
                                final SVNDepth depth, final boolean ignoreExternals, @Nullable final Listener listener) {
    if (! target.exists()) {
      target.mkdirs();
    }

    final WorkingCopyFormat selectedFormat = promptForWCopyFormat(target, project);
    // UNKNOWN here means operation was cancelled
    if (selectedFormat != WorkingCopyFormat.UNKNOWN) {
      checkout(project, target, url, revision, depth, ignoreExternals, listener, selectedFormat);
    }
  }

  public static void checkout(final Project project,
                               final File target,
                               final String url,
                               final SVNRevision revision,
                               final SVNDepth depth,
                               final boolean ignoreExternals,
                               final Listener listener, final WorkingCopyFormat selectedFormat) {
    final Ref<Boolean> checkoutSuccessful = new Ref<Boolean>();
    final Exception[] exception = new Exception[1];
    final Task.Backgroundable checkoutBackgroundTask = new Task.Backgroundable(project,
                     SvnBundle.message("message.title.check.out"), true, VcsConfiguration.getInstance(project).getCheckoutOption()) {
      public void run(@NotNull final ProgressIndicator indicator) {
        SvnWorkingCopyFormatHolder.setPresetFormat(selectedFormat);

        SvnVcs vcs = SvnVcs.getInstance(project);
        // TODO: made this way to preserve existing logic, but probably this check could be omitted as setPresetFormat(selectedFormat) invoked above
        WorkingCopyFormat format = !WorkingCopyFormat.ONE_DOT_SEVEN.equals(SvnWorkingCopyFormatHolder.getPresetFormat())
                                   ? WorkingCopyFormat.ONE_DOT_SIX
                                   : selectedFormat;
        ISVNEventHandler handler = new CheckoutEventHandler(vcs, false, ProgressManager.getInstance().getProgressIndicator());
        ProgressManager.progress(SvnBundle.message("progress.text.checking.out", target.getAbsolutePath()));
        try {
          // TODO: probably rewrite some logic to force ClientFactory provide supported versions (or create special client for that)
          vcs.getFactoryFromSettings().createCheckoutClient()
            .checkout(SvnTarget.fromURL(SVNURL.parseURIEncoded(url)), target, revision, depth, ignoreExternals, format, handler);
          ProgressManager.checkCanceled();
          checkoutSuccessful.set(Boolean.TRUE);
        }
        catch (SVNCancelException ignore) {
        }
        catch (SVNException e) {
          exception[0] = e;
        }
        catch (VcsException e) {
          exception[0] = e;
        }
        finally {
          SvnWorkingCopyFormatHolder.setPresetFormat(null);
        }
      }

      public void onCancel() {
        onSuccess();
      }

      public void onSuccess() {
        if (exception[0] != null) {
          Messages.showErrorDialog(SvnBundle.message("message.text.cannot.checkout", exception[0].getMessage()), SvnBundle.message("message.title.check.out"));
        }

        final VirtualFile vf = RefreshVFsSynchronously.findCreatedFile(target);
        if (vf != null) {
          vf.refresh(true, true, new Runnable() {
            public void run() {
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  notifyListener();
                }
              });
            }
          });
        }
        else {
          notifyListener();
        }
      }

      private void notifyListener() {
        notifyRootManagerIfUnderProject(project, target);
        if (listener != null) {
          if (!checkoutSuccessful.isNull()) {
            listener.directoryCheckedOut(target, SvnVcs.getKey());
          }
          listener.checkoutCompleted();
        }
      }
    };
    ProgressManager.getInstance().run(checkoutBackgroundTask);
  }

  private static void notifyRootManagerIfUnderProject(final Project project, final File directory) {
    if (project.isDefault()) return;
    final ProjectLevelVcsManagerEx plVcsManager = ProjectLevelVcsManagerEx.getInstanceEx(project);
    final SvnVcs vcs = (SvnVcs) plVcsManager.findVcsByName(SvnVcs.VCS_NAME);

    final VirtualFile[] files = vcs.getSvnFileUrlMapping().getNotFilteredRoots();
    for (VirtualFile file : files) {
      if (FileUtil.isAncestor(new File(file.getPath()), directory, false)) {
        // todo: should be done like auto detection
        plVcsManager.fireDirectoryMappingsChanged();
        return;
      }
    }
  }

  public static boolean promptForWCFormatAndSelect(final File target, final Project project) {
    final WorkingCopyFormat result = promptForWCopyFormat(target, project);
    if (result != WorkingCopyFormat.UNKNOWN) {
      SvnWorkingCopyFormatHolder.setPresetFormat(result);
    }
    return result != WorkingCopyFormat.UNKNOWN;
  }

  @NotNull
  private static WorkingCopyFormat promptForWCopyFormat(final File target, final Project project) {
    WorkingCopyFormat format = WorkingCopyFormat.UNKNOWN;
    final Ref<Boolean> wasOk = new Ref<Boolean>();
    while ((format == WorkingCopyFormat.UNKNOWN) && (! Boolean.FALSE.equals(wasOk.get()))) {
      format = SvnFormatSelector.showUpgradeDialog(target, project, true, WorkingCopyFormat.ONE_DOT_SEVEN, wasOk);
    }
    return Boolean.TRUE.equals(wasOk.get()) ? format : WorkingCopyFormat.UNKNOWN;
  }

  public static void doExport(final Project project, final File target, final String url, final SVNDepth depth,
                              final boolean ignoreExternals, final boolean force, final String eolStyle) {
    try {
      final SVNException[] exception = new SVNException[1];
      final SVNUpdateClient client = SvnVcs.getInstance(project).createUpdateClient();

      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          client.setEventHandler(new CheckoutEventHandler(SvnVcs.getInstance(project), true, progressIndicator));
          client.setIgnoreExternals(ignoreExternals);
          try {
            progressIndicator.setText(SvnBundle.message("progress.text.export", target.getAbsolutePath()));
            client.doExport(SVNURL.parseURIEncoded(url), target, SVNRevision.UNDEFINED, SVNRevision.HEAD, eolStyle, force, depth);
          }
          catch (SVNException e) {
            exception[0] = e;
          }
          finally {
            client.setIgnoreExternals(false);
            client.setEventHandler(null);
          }
        }
      }, SvnBundle.message("message.title.export"), true, project);
      if (exception[0] != null) {
        throw exception[0];
      }
    }
    catch (SVNException e1) {
      Messages.showErrorDialog(SvnBundle.message("message.text.cannot.export", e1.getMessage()), SvnBundle.message("message.title.export"));
    }
  }

  public static void doImport(final Project project, final File target, final SVNURL url, final SVNDepth depth,
                              final boolean includeIgnored, final String message) {
    final Ref<String> errorMessage = new Ref<String>();
    final SVNCommitClient client = SvnVcs.getInstance(project).createCommitClient();
    final String targetPath = FileUtil.toSystemIndependentName(target.getAbsolutePath());

    ExclusiveBackgroundVcsAction.run(project, new Runnable() {
      public void run() {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
          public void run() {
            final FileIndexFacade facade = PeriodicalTasksCloser.getInstance().safeGetService(project, FileIndexFacade.class);
            ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
            client.setEventHandler(new CheckoutEventHandler(SvnVcs.getInstance(project), true, progressIndicator));
            try {
              progressIndicator.setText(SvnBundle.message("progress.text.import", target.getAbsolutePath()));

              final VirtualFile targetVf = SvnUtil.getVirtualFile(targetPath);
              if (targetVf == null) {
                errorMessage.set("Can not find file: " + targetPath);
              } else {
                final boolean isInContent = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
                  @Override
                  public Boolean compute() {
                    return facade.isInContent(targetVf);
                  }
                });
                SVNCommitInfo info;
                if (project.isDefault() || !isInContent) {
                  // do not pay attention to ignored/excluded settings
                  info = client.doImport(target, url, message, null, !includeIgnored, false, depth);
                } else {
                  client.setCommitHandler(new MyFilter(LocalFileSystem.getInstance(), new SvnExcludingIgnoredOperation.Filter(project)));
                  info = client.doImport(target, url, message, null, !includeIgnored, false, depth);
                }
                if (info.getNewRevision() > 0) {
                  StatusBar.Info.set(SvnBundle.message("status.text.comitted.revision", info.getNewRevision()), project);
                }
              }
            }
            catch (SVNException e) {
              errorMessage.set(e.getMessage());
            }
            finally {
              client.setIgnoreExternals(false);
              client.setEventHandler(null);
            }
          }
        }, SvnBundle.message("message.title.import"), true, project);
      }
    });
    
    if (! errorMessage.isNull()) {
      Messages.showErrorDialog(SvnBundle.message("message.text.cannot.import", errorMessage.get()), SvnBundle.message("message.title.import"));
    }
  }

  private static class MyFilter extends DefaultSVNCommitHandler implements ISVNFileFilter {
    private final LocalFileSystem myLfs;
    private final SvnExcludingIgnoredOperation.Filter myFilter;

    private MyFilter(LocalFileSystem lfs, SvnExcludingIgnoredOperation.Filter filter) {
      myLfs = lfs;
      myFilter = filter;
    }

    public boolean accept(final File file) throws SVNException {
      final VirtualFile vf = myLfs.findFileByIoFile(file);
      return vf != null && myFilter.accept(vf);
    }
  }

  public String getVcsName() {
    return "_Subversion";
  }

}


