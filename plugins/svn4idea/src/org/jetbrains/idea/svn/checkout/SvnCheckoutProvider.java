/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.CalledInAwt;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.actions.ExclusiveBackgroundVcsAction;
import org.jetbrains.idea.svn.actions.SvnExcludingIgnoredOperation;
import org.jetbrains.idea.svn.api.ClientFactory;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.checkin.CommitEventHandler;
import org.jetbrains.idea.svn.checkin.IdeaCommitHandler;
import org.jetbrains.idea.svn.dialogs.CheckoutDialog;
import org.jetbrains.idea.svn.dialogs.UpgradeFormatDialog;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.DefaultSVNCommitHandler;
import org.tmatesoft.svn.core.wc.ISVNCommitHandler;
import org.tmatesoft.svn.core.wc.ISVNFileFilter;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.application.ModalityState.any;
import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.WorkingCopyFormat.UNKNOWN;

public class SvnCheckoutProvider implements CheckoutProvider {

  public void doCheckout(@NotNull final Project project, Listener listener) {
    // TODO: Several dialogs is invoked while dialog.show() - seems code should be rewritten to be more transparent
    CheckoutDialog dialog = new CheckoutDialog(project, listener);
    dialog.show();
  }

  public static void doCheckout(@NotNull Project project, @NotNull File target, final String url, final SVNRevision revision,
                                final Depth depth, final boolean ignoreExternals, @Nullable final Listener listener) {
    if (! target.exists()) {
      target.mkdirs();
    }

    final WorkingCopyFormat selectedFormat = promptForWCopyFormat(target, project);
    // UNKNOWN here means operation was cancelled
    if (selectedFormat != UNKNOWN) {
      checkout(project, target, url, revision, depth, ignoreExternals, listener, selectedFormat);
    }
  }

  @NotNull
  public static ClientFactory getFactory(@NotNull SvnVcs vcs, @NotNull WorkingCopyFormat format) throws VcsException {
    ClientFactory settingsFactory = vcs.getFactoryFromSettings();
    ClientFactory otherFactory = vcs.getOtherFactory();
    List<WorkingCopyFormat> settingsFactoryFormats = settingsFactory.createCheckoutClient().getSupportedFormats();
    List<WorkingCopyFormat> otherFactoryFormats = CheckoutFormatFromUserProvider.getOtherFactoryFormats(otherFactory);

    return settingsFactoryFormats.contains(format) || !otherFactoryFormats.contains(format) ? settingsFactory : otherFactory;
  }

  public static void checkout(final Project project,
                               final File target,
                               final String url,
                               final SVNRevision revision,
                               final Depth depth,
                               final boolean ignoreExternals,
                               final Listener listener, final WorkingCopyFormat selectedFormat) {
    final Ref<Boolean> checkoutSuccessful = new Ref<>();
    final Exception[] exception = new Exception[1];
    final Task.Backgroundable checkoutBackgroundTask = new Task.Backgroundable(project,
                     message("message.title.check.out"), true, VcsConfiguration.getInstance(project).getCheckoutOption()) {
      public void run(@NotNull final ProgressIndicator indicator) {
        final WorkingCopyFormat format = selectedFormat == null ? UNKNOWN : selectedFormat;

        SvnWorkingCopyFormatHolder.setPresetFormat(format);

        SvnVcs vcs = SvnVcs.getInstance(project);
        ProgressTracker handler = new CheckoutEventHandler(vcs, false, ProgressManager.getInstance().getProgressIndicator());
        ProgressManager.progress(message("progress.text.checking.out", target.getAbsolutePath()));
        try {
          getFactory(vcs, format).createCheckoutClient()
            .checkout(SvnTarget.fromURL(SVNURL.parseURIEncoded(url)), target, revision, depth, ignoreExternals, true, format, handler);
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
          showErrorDialog(message("message.text.cannot.checkout", exception[0].getMessage()), message("message.title.check.out"));
        }

        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target);
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

  @CalledInAwt
  @NotNull
  public static WorkingCopyFormat promptForWCopyFormat(@NotNull File target, @NotNull Project project) {
    return new CheckoutFormatFromUserProvider(project, target).prompt();
  }

  public static void doExport(final Project project, final File target, final SVNURL url, final Depth depth,
                              final boolean ignoreExternals, final boolean force, final String eolStyle) {
    try {
      final VcsException[] exception = new VcsException[1];
      final SvnVcs vcs = SvnVcs.getInstance(project);

      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          ProgressTracker handler = new CheckoutEventHandler(vcs, true, progressIndicator);
          try {
            progressIndicator.setText(message("progress.text.export", target.getAbsolutePath()));

            SvnTarget from = SvnTarget.fromURL(url);
            ExportClient client = vcs.getFactoryFromSettings().createExportClient();
            client.export(from, target, SVNRevision.HEAD, depth, eolStyle, force, ignoreExternals, handler);
          }
          catch (VcsException e) {
            exception[0] = e;
          }
        }
      }, message("message.title.export"), true, project);
      if (exception[0] != null) {
        throw exception[0];
      }
    }
    catch (VcsException e1) {
      showErrorDialog(message("message.text.cannot.export", e1.getMessage()), message("message.title.export"));
    }
  }

  public static void doImport(final Project project, final File target, final SVNURL url, final Depth depth,
                              final boolean includeIgnored, final String message) {
    final Ref<String> errorMessage = new Ref<>();
    final SvnVcs vcs = SvnVcs.getInstance(project);
    final String targetPath = FileUtil.toSystemIndependentName(target.getAbsolutePath());

    ExclusiveBackgroundVcsAction.run(project, new Runnable() {
      public void run() {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
          public void run() {
            final FileIndexFacade facade = PeriodicalTasksCloser.getInstance().safeGetService(project, FileIndexFacade.class);
            ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
            try {
              progressIndicator.setText(message("progress.text.import", target.getAbsolutePath()));

              final VirtualFile targetVf = SvnUtil.getVirtualFile(targetPath);
              if (targetVf == null) {
                errorMessage.set("Can not find file: " + targetPath);
              } else {
                final boolean isInContent = getApplication().runReadAction(new Computable<Boolean>() {
                  @Override
                  public Boolean compute() {
                    return facade.isInContent(targetVf);
                  }
                });
                CommitEventHandler handler = new IdeaCommitHandler(progressIndicator);
                boolean useFileFilter = !project.isDefault() && isInContent;
                ISVNCommitHandler commitHandler =
                  useFileFilter ? new MyFilter(LocalFileSystem.getInstance(), new SvnExcludingIgnoredOperation.Filter(project)) : null;
                long revision = vcs.getFactoryFromSettings().createImportClient()
                  .doImport(target, url, depth, message, includeIgnored, handler, commitHandler);

                if (revision > 0) {
                  StatusBar.Info.set(message("status.text.comitted.revision", revision), project);
                }
              }
            }
            catch (VcsException e) {
              errorMessage.set(e.getMessage());
            }
          }
        }, message("message.title.import"), true, project);
      }
    });
    
    if (! errorMessage.isNull()) {
      showErrorDialog(message("message.text.cannot.import", errorMessage.get()), message("message.title.import"));
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

  public static class CheckoutFormatFromUserProvider {

    private static final Logger LOG = Logger.getInstance(CheckoutFormatFromUserProvider.class);

    @NotNull private final Project myProject;
    @NotNull private final SvnVcs myVcs;
    @NotNull private final File myPath;

    @NotNull private final AtomicReference<String> error;

    public CheckoutFormatFromUserProvider(@NotNull Project project, @NotNull File path) {
      myProject = project;
      myVcs = SvnVcs.getInstance(project);
      myPath = path;

      error = new AtomicReference<>();
    }

    @CalledInAwt
    public WorkingCopyFormat prompt() {
      assert !getApplication().isUnitTestMode();

      final WorkingCopyFormat result = displayUpgradeDialog();

      getApplication().getMessageBus().syncPublisher(SvnVcs.WC_CONVERTED).run();

      return result;
    }

    @NotNull
    private WorkingCopyFormat displayUpgradeDialog() {
      final UpgradeFormatDialog dialog = new UpgradeFormatDialog(myProject, myPath, false);
      final ModalityState dialogState = any();

      dialog.startLoading();
      getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          final List<WorkingCopyFormat> formats = loadSupportedFormats();

          getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              final String errorMessage = error.get();

              if (errorMessage != null) {
                dialog.doCancelAction();
                showErrorDialog(message("message.text.cannot.load.supported.formats", errorMessage),
                                message("message.title.check.out"));
              }
              else {
                dialog.setSupported(formats);
                dialog.setData(getFirstItem(formats, UNKNOWN));
                dialog.stopLoading();
              }
            }
          }, dialogState);
        }
      });

      return dialog.showAndGet() ? dialog.getUpgradeMode() : UNKNOWN;
    }

    @NotNull
    private List<WorkingCopyFormat> loadSupportedFormats() {
      List<WorkingCopyFormat> result = ContainerUtil.newArrayList();

      try {
        result.addAll(myVcs.getFactoryFromSettings().createCheckoutClient().getSupportedFormats());
        result.addAll(getOtherFactoryFormats(myVcs.getOtherFactory()));
      }
      catch (VcsException e) {
        error.set(e.getMessage());
      }

      return result;
    }

    @NotNull
    private static List<WorkingCopyFormat> getOtherFactoryFormats(@NotNull ClientFactory otherFactory) {
      List<WorkingCopyFormat> result;

      try {
        result = otherFactory.createCheckoutClient().getSupportedFormats();
      }
      catch (VcsException e) {
        // do not add error as it is just usability fix and "other factory" could be incorrectly configured (for instance, invalid
        // executable path)
        result = ContainerUtil.newArrayList();
        LOG.info("Failed to get checkout formats from other factory", e);
      }

      return result;
    }
  }
}