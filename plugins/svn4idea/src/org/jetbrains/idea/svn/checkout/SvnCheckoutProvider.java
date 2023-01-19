// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.ui.VcsCloneComponent;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneStatus;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneTask;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneTaskInfo;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.actions.ExclusiveBackgroundVcsAction;
import org.jetbrains.idea.svn.actions.SvnExcludingIgnoredOperation;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.checkin.CommitEventHandler;
import org.jetbrains.idea.svn.checkin.IdeaCommitHandler;
import org.jetbrains.idea.svn.dialogs.CheckoutDialog;
import org.jetbrains.idea.svn.dialogs.SvnCloneDialogExtension;
import org.jetbrains.idea.svn.dialogs.UpgradeFormatDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.application.ModalityState.any;
import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.parseUrl;
import static org.jetbrains.idea.svn.WorkingCopyFormat.UNKNOWN;

public class SvnCheckoutProvider implements CheckoutProvider {

  @Override
  public void doCheckout(@NotNull final Project project, Listener listener) {
    // TODO: Several dialogs is invoked while dialog.show() - seems code should be rewritten to be more transparent
    CheckoutDialog dialog = new CheckoutDialog(project, listener);
    dialog.show();
  }

  /**
   * @deprecated use {@link #doCheckout(Project, File, Url, Revision, Depth, boolean, Listener)}
   */
  @Deprecated(forRemoval = true)
  public static void doCheckout(@NotNull Project project, @NotNull File target, final String url, final Revision revision,
                                final Depth depth, final boolean ignoreExternals, @Nullable final Listener listener) {
    doCheckout(project, target, parseUrl(url), revision, depth, ignoreExternals, listener);
  }

  public static void doCheckout(@NotNull Project project,
                                @NotNull File target,
                                @NotNull Url url,
                                Revision revision,
                                Depth depth,
                                boolean ignoreExternals,
                                @Nullable Listener listener) {
    if (!target.exists()) {
      target.mkdirs();
    }

    final WorkingCopyFormat selectedFormat = promptForWCopyFormat(target, project);
    // UNKNOWN here means operation was cancelled
    if (selectedFormat != UNKNOWN) {
      checkout(project, target, url, revision, depth, ignoreExternals, listener, selectedFormat);
    }
  }

  @NotNull
  public static ClientFactory getFactory(@NotNull SvnVcs vcs) {
    return vcs.getFactoryFromSettings();
  }


  public static void checkout(Project project,
                              File target,
                              @NotNull Url url,
                              Revision revision,
                              Depth depth,
                              boolean ignoreExternals,
                              Listener listener,
                              WorkingCopyFormat selectedFormat) {
    String projectAbsolutePath = target.getAbsolutePath();
    String projectPath = FileUtilRt.toSystemIndependentName(projectAbsolutePath);

    CloneTask cloneTask = new CloneTask() {
      final Ref<Boolean> checkoutSuccessful = new Ref<>();

      @NotNull
      @Override
      public CloneTaskInfo taskInfo() {
        return new CloneTaskInfo(message("progress.title.check.out"),
                                 message("progress.title.check.out.cancel"),
                                 message("checkout.repository"),
                                 message("checkout.repository.tooltip"),
                                 message("checkout.repository.failed"),
                                 message("checkout.repository.canceled"),
                                 message("checkout.stop.message.title"),
                                 message("checkout.stop.message.description", url.toString()));
      }

      @NotNull
      @Override
      public CloneStatus run(@NotNull ProgressIndicator indicator) {
        WorkingCopyFormat format = selectedFormat == null ? UNKNOWN : selectedFormat;
        SvnVcs vcs = SvnVcs.getInstance(project);
        ProgressTracker handler = new CheckoutEventHandler(vcs, false, ProgressManager.getInstance().getProgressIndicator());
        indicator.setText(message("progress.text.checking.out", target.getAbsolutePath()));
        try {
          getFactory(vcs).createCheckoutClient()
            .checkout(Target.on(url), target, revision, depth, ignoreExternals, true, format, handler);
          checkoutSuccessful.set(Boolean.TRUE);

          return CloneStatus.SUCCESS;
        }
        catch (VcsException exception) {
          getApplication().invokeLater(() -> {
            showErrorDialog(message("message.text.cannot.checkout", exception.getMessage()), message("dialog.title.check.out"));
          });
          return CloneStatus.FAILURE;
        }
        finally {
          VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target);
          if (vf != null) {
            vf.refresh(true, true, () -> getApplication().executeOnPooledThread(() -> notifyListener()));
          }
          else {
            notifyListener();
          }
        }
      }

      @RequiresBackgroundThread
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

    CloneableProjectsService.getInstance().runCloneTask(projectPath, cloneTask);
  }

  private static void notifyRootManagerIfUnderProject(@NotNull Project project, @NotNull File directory) {
    if (project.isDefault()) return;

    VirtualFile[] files = SvnVcs.getInstance(project).getSvnFileUrlMapping().getNotFilteredRoots();
    for (VirtualFile file : files) {
      if (FileUtil.isAncestor(virtualToIoFile(file), directory, false)) {
        // todo: should be done like auto detection
        ProjectLevelVcsManagerEx.getInstanceEx(project).fireDirectoryMappingsChanged();
        return;
      }
    }
  }

  @RequiresEdt
  @NotNull
  public static WorkingCopyFormat promptForWCopyFormat(@NotNull File target, @NotNull Project project) {
    return new CheckoutFormatFromUserProvider(project, target).prompt();
  }

  public static void doExport(final Project project, final File target, final Url url, final Depth depth,
                              final boolean ignoreExternals, final boolean force, final String eolStyle) {
    try {
      final VcsException[] exception = new VcsException[1];
      final SvnVcs vcs = SvnVcs.getInstance(project);

      ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        ProgressTracker handler = new CheckoutEventHandler(vcs, true, progressIndicator);
        try {
          progressIndicator.setText(message("progress.text.export", target.getAbsolutePath()));

          Target from = Target.on(url);
          ExportClient client = vcs.getFactoryFromSettings().createExportClient();
          client.export(from, target, Revision.HEAD, depth, eolStyle, force, ignoreExternals, handler);
        }
        catch (VcsException e) {
          exception[0] = e;
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

  public static void doImport(final Project project, final File target, final Url url, final Depth depth,
                              final boolean includeIgnored, final String message) {
    final Ref<@Nls String> errorMessage = new Ref<>();
    final SvnVcs vcs = SvnVcs.getInstance(project);
    final String targetPath = target.getAbsolutePath();

    ExclusiveBackgroundVcsAction.run(project, () -> ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      final FileIndexFacade facade = project.getService(FileIndexFacade.class);
      ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
      try {
        progressIndicator.setText(message("progress.text.import", targetPath));

        final VirtualFile targetVf = SvnUtil.getVirtualFile(targetPath);
        if (targetVf == null) {
          errorMessage.set(message("error.can.not.find.file", targetPath));
        }
        else {
          final boolean isInContent = ReadAction.compute(() -> facade.isInContent(targetVf));
          CommitEventHandler handler = new IdeaCommitHandler(progressIndicator);
          boolean useFileFilter = !project.isDefault() && isInContent;
          Predicate<File> filter = useFileFilter ? new MyFilter(new SvnExcludingIgnoredOperation.Filter(project)) : null;
          long revision =
            vcs.getFactoryFromSettings().createImportClient().doImport(target, url, depth, message, includeIgnored, handler, filter);

          if (revision > 0) {
            StatusBar.Info.set(message("status.text.committed.revision", revision), project);
          }
        }
      }
      catch (VcsException e) {
        errorMessage.set(e.getMessage());
      }
    }, message("message.title.import"), true, project));

    if (!errorMessage.isNull()) {
      showErrorDialog(message("message.text.cannot.import", errorMessage.get()), message("message.title.import"));
    }
  }

  private static final class MyFilter implements Predicate<File> {
    @NotNull private final LocalFileSystem myLfs = LocalFileSystem.getInstance();
    @NotNull private final SvnExcludingIgnoredOperation.Filter myFilter;

    private MyFilter(@NotNull SvnExcludingIgnoredOperation.Filter filter) {
      myFilter = filter;
    }

    @Override
    public boolean test(@NotNull File file) {
      final VirtualFile vf = myLfs.findFileByIoFile(file);
      return vf != null && myFilter.accept(vf);
    }
  }

  @Override
  public @NotNull String getVcsName() {
    return message("svn.name.with.mnemonic");
  }

  public static class CheckoutFormatFromUserProvider {

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

    @RequiresEdt
    public WorkingCopyFormat prompt() {
      assert !getApplication().isUnitTestMode();

      final WorkingCopyFormat result = displayUpgradeDialog();

      BackgroundTaskUtil.syncPublisher(SvnVcs.WC_CONVERTED).run();

      return result;
    }

    @NotNull
    private WorkingCopyFormat displayUpgradeDialog() {
      final UpgradeFormatDialog dialog = new UpgradeFormatDialog(myProject, myPath, false);
      final ModalityState dialogState = any();

      dialog.startLoading();
      getApplication().executeOnPooledThread(() -> {
        final List<WorkingCopyFormat> formats = loadSupportedFormats();

        getApplication().invokeLater(() -> {
          final String errorMessage = error.get();

          if (errorMessage != null) {
            dialog.doCancelAction();
            showErrorDialog(message("message.text.cannot.load.supported.formats", errorMessage),
                            message("dialog.title.check.out"));
          }
          else {
            dialog.setSupported(formats);
            dialog.setData(getFirstItem(formats, UNKNOWN));
            dialog.stopLoading();
          }
        }, dialogState);
      });

      return dialog.showAndGet() ? dialog.getUpgradeMode() : UNKNOWN;
    }

    @NotNull
    private List<WorkingCopyFormat> loadSupportedFormats() {
      List<WorkingCopyFormat> result = new ArrayList<>();

      try {
        result.addAll(myVcs.getFactoryFromSettings().createCheckoutClient().getSupportedFormats());
      }
      catch (VcsException e) {
        error.set(e.getMessage());
      }

      return result;
    }
  }

  @NotNull
  @Override
  public VcsCloneComponent buildVcsCloneComponent(@NotNull Project project,
                                                  @NotNull ModalityState modalityState,
                                                  @NotNull VcsCloneDialogComponentStateListener dialogStateListener) {
    return new SvnCloneDialogExtension(project);
  }
}