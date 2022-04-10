// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopiesContent;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.actions.CleanupWorker;
import org.jetbrains.idea.svn.api.ClientFactory;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.branchConfig.BranchConfigurationDialog;
import org.jetbrains.idea.svn.branchConfig.SelectBranchPopup;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.integrate.MergeContext;
import org.jetbrains.idea.svn.integrate.QuickMerge;
import org.jetbrains.idea.svn.integrate.QuickMergeInteractionImpl;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.io.File;
import java.util.*;

import static com.intellij.notification.NotificationAction.createSimpleExpiring;
import static com.intellij.notification.NotificationDisplayType.STICKY_BALLOON;
import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.ui.Messages.showWarningDialog;
import static com.intellij.openapi.util.text.StringUtil.notNullize;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.ui.JBUI.Borders.empty;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static org.jetbrains.idea.svn.SvnBundle.message;

public class CopiesPanel extends SimpleToolWindowPanel {

  private static final Logger LOG = Logger.getInstance(CopiesPanel.class);

  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Svn Roots Detection Errors");

  private static final String TOOLBAR_GROUP = "Svn.WorkingCopiesView.Toolbar";
  private static final String TOOLBAR_PLACE = "Svn.WorkingCopiesView";

  private final Project myProject;
  private final JPanel myPanel = new JBPanel<>(new VerticalLayout(8));
  private boolean isRefreshing;

  private static final @NonNls String HELP_ID = "reference.vcs.svn.working.copies.information";

  final static @NonNls String CHANGE_FORMAT = "CHANGE_FORMAT";
  final static @NonNls String CLEANUP = "CLEANUP";
  final static @NonNls String FIX_DEPTH = "FIX_DEPTH";
  final static @NonNls String CONFIGURE_BRANCHES = "CONFIGURE_BRANCHES";
  final static @NonNls String MERGE_FROM = "MERGE_FROM";

  public CopiesPanel(@NotNull Project project) {
    super(false, true);
    myProject = project;
    myProject.getMessageBus().connect().subscribe(SvnVcs.ROOTS_RELOADED, (Consumer<Boolean>)this::rootsReloaded);

    myPanel.setBorder(empty(2, 4));
    setContent(createScrollPane(myPanel));

    ActionGroup toolbarGroup = (ActionGroup)ActionManager.getInstance().getAction(TOOLBAR_GROUP);
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, toolbarGroup, false);
    setToolbar(toolbar.getComponent());

    rootsReloaded(true);
    refresh();
  }

  @RequiresEdt
  public boolean isRefreshing() {
    return isRefreshing;
  }

  @RequiresEdt
  public void refresh() {
    if (isRefreshing) return;

    isRefreshing = true;
    getVcs().invokeRefreshSvnRoots();
  }

  @NotNull
  private SvnVcs getVcs() {
    return SvnVcs.getInstance(myProject);
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    if (PlatformCoreDataKeys.HELP_ID.is(dataId)) return HELP_ID;
    return super.getData(dataId);
  }

  private void rootsReloaded(boolean rootsChanged) {
    if (rootsChanged) {
      if (getApplication().isUnitTestMode()) {
        doRefresh();
      }
      else {
        getApplication().executeOnPooledThread(this::doRefresh);
      }
    }
    else {
      getApplication().invokeLater(() -> isRefreshing = false, ModalityState.NON_MODAL);
    }
  }

  private void doRefresh() {
    List<WCInfo> infoList = getVcs().getWcInfosWithErrors();
    boolean hasErrors = !getVcs().getSvnFileUrlMapping().getErrorRoots().isEmpty();
    List<WorkingCopyFormat> supportedFormats = getSupportedFormats();

    getApplication().invokeLater(() -> setWorkingCopies(infoList, hasErrors, supportedFormats), ModalityState.NON_MODAL);
  }

  @RequiresEdt
  private void setWorkingCopies(@NotNull List<WCInfo> infoList, boolean hasErrors, List<WorkingCopyFormat> supportedFormats) {
    infoList.sort(comparing(WCInfo::getPath));
    updateList(infoList, supportedFormats);
    isRefreshing = false;
    showErrorNotification(hasErrors);
  }

  private void updateList(@NotNull final List<WCInfo> infoList, @NotNull final List<WorkingCopyFormat> supportedFormats) {
    myPanel.removeAll();

    for (final WCInfo wcInfo : infoList) {
      final Collection<WorkingCopyFormat> upgradeFormats = getUpgradeFormats(wcInfo, supportedFormats);

      WorkingCopyInfoPanel infoPanel = new WorkingCopyInfoPanel();
      infoPanel.setInfo(wcInfo);
      infoPanel.setUpgradeFormats(upgradeFormats);
      infoPanel.setFocusable(true);
      infoPanel.setBorder(null);
      infoPanel.update();
      infoPanel.addHyperlinkListener(new HyperlinkListener() {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent e) {
          if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;

          performAction(wcInfo, upgradeFormats, infoPanel, e.getDescription());
        }
      });

      myPanel.add(infoPanel);
    }

    myPanel.revalidate();
    myPanel.repaint();
  }

  @NotNull
  private List<WorkingCopyFormat> getSupportedFormats() {
    List<WorkingCopyFormat> result = new ArrayList<>();
    ClientFactory factory = getVcs().getFactory();

    try {
      result.addAll(factory.createUpgradeClient().getSupportedFormats());
    }
    catch (VcsException e) {
      LOG.info(e);
    }

    return result;
  }

  public static Set<WorkingCopyFormat> getUpgradeFormats(@NotNull WCInfo info, @NotNull List<WorkingCopyFormat> supportedFormats) {
    Set<WorkingCopyFormat> canUpgradeTo = EnumSet.noneOf(WorkingCopyFormat.class);

    for (WorkingCopyFormat format : supportedFormats) {
      if (format.isOrGreater(info.getFormat())) {
        canUpgradeTo.add(format);
      }
    }
    canUpgradeTo.add(info.getFormat());

    return canUpgradeTo;
  }

  private boolean checkRoot(@NotNull VirtualFile root, @ActionText @NotNull String actionName) {
    if (root.isValid()) return true;

    showWarningDialog(myProject, message("dialog.message.invalid.working.copy.root", root.getPath()),
                      message("dialog.title.can.not.invoke.action", actionName));
    return false;
  }

  private void performAction(@NotNull WCInfo wcInfo,
                             @NotNull Collection<WorkingCopyFormat> upgradeFormats,
                             @NotNull WorkingCopyInfoPanel infoPanel,
                             @NonNls @Nullable String actionName) {
    if (CONFIGURE_BRANCHES.equals(actionName)) {
      configureBranches(wcInfo);
    }
    else if (FIX_DEPTH.equals(actionName)) {
      fixDepth(wcInfo);
    }
    else if (CHANGE_FORMAT.equals(actionName)) {
      changeFormat(wcInfo, upgradeFormats);
    }
    else if (MERGE_FROM.equals(actionName)) {
      mergeFrom(wcInfo, infoPanel);
    }
    else if (CLEANUP.equals(actionName)) {
      cleanup(wcInfo);
    }
  }

  private void configureBranches(@NotNull WCInfo info) {
    VirtualFile root = info.getRootInfo().getVirtualFile();
    if (!checkRoot(root, message("action.name.configure.branches"))) return;

    BranchConfigurationDialog.configureBranches(myProject, root);
  }

  private void fixDepth(@NotNull WCInfo info) {
    int result = Messages.showOkCancelDialog(myProject, message("dialog.message.set.working.copy.infinity.depth", info.getPath()),
                                             message("dialog.title.set.working.copy.infinity.depth"), Messages.getWarningIcon());
    if (result == Messages.OK) {
      // update of view will be triggered by roots changed event
      SvnCheckoutProvider.checkout(myProject, new File(info.getPath()), info.getUrl(), Revision.HEAD,
                                   Depth.INFINITY, false, null, info.getFormat());
    }
  }

  private void mergeFrom(@NotNull WCInfo info, @NotNull WorkingCopyInfoPanel infoPanel) {
    VirtualFile root = info.getRootInfo().getVirtualFile();
    if (!checkRoot(root, message("action.name.merge.from"))) return;

    SelectBranchPopup.showForBranchRoot(myProject, root, (project, configuration, branchUrl, revision) -> {
      try {
        Url workingCopyUrlInSelectedBranch = getCorrespondingUrlInOtherBranch(configuration, info.getUrl(), branchUrl);
        MergeContext mergeContext = new MergeContext(getVcs(), workingCopyUrlInSelectedBranch, info, branchUrl.getTail(), root);

        new QuickMerge(mergeContext, new QuickMergeInteractionImpl(mergeContext)).execute();
      }
      catch (SvnBindException e) {
        AbstractVcsHelper.getInstance(myProject).showError(e, message("dialog.title.merge.from.branch", branchUrl.getTail()));
      }
    }, message("popup.title.select.branch"), infoPanel);
  }

  private void cleanup(@NotNull WCInfo info) {
    VirtualFile root = info.getRootInfo().getVirtualFile();
    if (!checkRoot(root, message("cleanup.action.name"))) return;

    new CleanupWorker(getVcs(), singletonList(root)).execute();
  }

  @NotNull
  private static Url getCorrespondingUrlInOtherBranch(@NotNull SvnBranchConfigurationNew configuration,
                                                      @NotNull Url url,
                                                      @NotNull Url otherBranchUrl) throws SvnBindException {
    return otherBranchUrl.appendPath(notNullize(configuration.getRelativeUrl(url)), false);
  }

  private void changeFormat(@NotNull final WCInfo wcInfo, @NotNull final Collection<WorkingCopyFormat> supportedFormats) {
    ChangeFormatDialog dialog = new ChangeFormatDialog(myProject, new File(wcInfo.getPath()), false, !wcInfo.isIsWcRoot());

    dialog.setSupported(supportedFormats);
    dialog.setData(wcInfo.getFormat());
    if (!dialog.showAndGet()) {
      return;
    }
    final WorkingCopyFormat newFormat = dialog.getUpgradeMode();
    if (!wcInfo.getFormat().equals(newFormat)) {
      StoreUtil.saveDocumentsAndProjectSettings(myProject);
      final Task.Backgroundable task = new SvnFormatWorker(myProject, newFormat, wcInfo) {
        @Override
        public void onSuccess() {
          super.onSuccess();
          refresh();
        }
      };
      ProgressManager.getInstance().run(task);
    }
  }

  private void showErrorNotification(boolean hasErrors) {
    NotificationsManager manager = NotificationsManager.getNotificationsManager();
    ErrorsFoundNotification[] notifications = manager.getNotificationsOfType(ErrorsFoundNotification.class, myProject);

    if (hasErrors) {
      // do not show notification if it is already shown
      if (notifications.length == 0) {
        new ErrorsFoundNotification(myProject).notify(myProject.isDefault() ? null : myProject);
      }
    } else {
      // expire notification
      for (ErrorsFoundNotification notification : notifications) {
        notification.expire();
      }
    }
  }

  private static final class ErrorsFoundNotification extends Notification {
    private ErrorsFoundNotification(@NotNull final Project project) {
      super(NOTIFICATION_GROUP.getDisplayId(), "", message("subversion.roots.detection.errors.found.description"), NotificationType.ERROR);

      addAction(
        createSimpleExpiring(message("subversion.roots.detection.errors.found.action.text"), () -> WorkingCopiesContent.show(project)));
    }
  }
}