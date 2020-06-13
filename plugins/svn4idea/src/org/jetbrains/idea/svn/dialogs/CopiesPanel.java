// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
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

import static com.intellij.notification.NotificationDisplayType.STICKY_BALLOON;
import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.ui.Messages.showWarningDialog;
import static com.intellij.openapi.util.text.StringUtil.notNullize;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.ui.JBUI.Borders.empty;
import static com.intellij.util.ui.JBUI.scale;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;

public class CopiesPanel extends SimpleToolWindowPanel {

  private static final Logger LOG = Logger.getInstance(CopiesPanel.class);

  private static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup("Svn Roots Detection Errors", STICKY_BALLOON, true);

  private static final String TOOLBAR_GROUP = "Svn.WorkingCopiesView.Toolbar";
  private static final String TOOLBAR_PLACE = "Svn.WorkingCopiesView";

  private final Project myProject;
  private final JPanel myPanel = new JBPanel<>(new VerticalLayout(scale(8)));
  private boolean isRefreshing;

  private final static String CHANGE_FORMAT = "CHANGE_FORMAT";
  private final static String CLEANUP = "CLEANUP";
  private final static String FIX_DEPTH = "FIX_DEPTH";
  private final static String CONFIGURE_BRANCHES = "CONFIGURE_BRANCHES";
  private final static String MERGE_FROM = "MERGE_FROM";

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

  @CalledInAwt
  public boolean isRefreshing() {
    return isRefreshing;
  }

  @CalledInAwt
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
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return "reference.vcs.svn.working.copies.information";
    }
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

  @CalledInAwt
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

  static String formatWc(@NotNull WCInfo info, @NotNull Collection<WorkingCopyFormat> upgradeFormats) {
    final StringBuilder sb = new StringBuilder().append("<table>");

    sb.append("<tr valign=\"top\"><td colspan=\"3\"><b>").append(info.getPath()).append("</b></td></tr>");
    if (info.hasError()) {
      sb.append("<tr valign=\"top\"><td>URL:</td><td colspan=\"2\" color=\"").append(ColorUtil.toHex(JBColor.red)).append("\">")
        .append(info.getErrorMessage()).append("</td></tr>");
    }
    else {
      sb.append("<tr valign=\"top\"><td>URL:</td><td colspan=\"2\">").append(info.getUrl().toDecodedString()).append("</td></tr>");
    }
    if (upgradeFormats.size() > 1) {
      sb.append("<tr valign=\"top\"><td>Format:</td><td>").append(info.getFormat().getName()).append("</td><td><a href=\"").
        append(CHANGE_FORMAT).append("\">Change</a></td></tr>");
    } else {
      sb.append("<tr valign=\"top\"><td>Format:</td><td colspan=\"2\">").append(info.getFormat().getName()).append("</td></tr>");
    }

    if (!Depth.INFINITY.equals(info.getStickyDepth()) && !info.hasError()) {
      // can fix
      sb.append("<tr valign=\"top\"><td>Depth:</td><td>").append(info.getStickyDepth().getName()).append("</td><td><a href=\"").
        append(FIX_DEPTH).append("\">Fix</a></td></tr>");
    } else {
      sb.append("<tr valign=\"top\"><td>Depth:</td><td colspan=\"2\">").append(info.getStickyDepth().getName()).append("</td></tr>");
    }

    final NestedCopyType type = info.getType();
    if (NestedCopyType.external.equals(type) || NestedCopyType.switched.equals(type)) {
      sb.append("<tr valign=\"top\"><td colspan=\"3\"><i>").append(type.getName()).append("</i></td></tr>");
    }
    if (info.isIsWcRoot()) {
      sb.append("<tr valign=\"top\"><td colspan=\"3\"><i>").append("Working copy root</i></td></tr>");
    }
    if (!info.hasError()) {
      if (info.getFormat().isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN)) {
        sb.append("<tr valign=\"top\"><td colspan=\"3\"><a href=\"").append(CLEANUP).append("\">Cleanup</a></td></tr>");
      }
      sb.append("<tr valign=\"top\"><td colspan=\"3\"><a href=\"").append(CONFIGURE_BRANCHES).append("\">Configure Branches</a></td></tr>");
      sb.append("<tr valign=\"top\"><td colspan=\"3\"><a href=\"").append(MERGE_FROM).append("\"><b>Merge From...</b></a></i></td></tr>");

      sb.append("</table>");
    }
    return sb.toString();
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

  private boolean checkRoot(@NotNull VirtualFile root, @NotNull String actionName) {
    if (root.isValid()) return true;

    showWarningDialog(myProject, "Invalid working copy root: " + root.getPath(), "Can not " + actionName);
    return false;
  }

  private void performAction(@NotNull WCInfo wcInfo,
                             @NotNull Collection<WorkingCopyFormat> upgradeFormats,
                             @NotNull WorkingCopyInfoPanel infoPanel,
                             @Nullable String actionName) {
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
    if (!checkRoot(root, " invoke Configure Branches")) return;

    BranchConfigurationDialog.configureBranches(myProject, root);
  }

  private void fixDepth(@NotNull WCInfo info) {
    final int result =
      Messages.showOkCancelDialog(myProject,
                                  "You are going to checkout into '" + info.getPath() + "' with 'infinity' depth.\n" +
                                  "This will update your working copy to HEAD revision as well.",
                                  "Set Working Copy Infinity Depth",
                                  Messages.getWarningIcon());
    if (result == Messages.OK) {
      // update of view will be triggered by roots changed event
      SvnCheckoutProvider.checkout(myProject, new File(info.getPath()), info.getUrl(), Revision.HEAD,
                                   Depth.INFINITY, false, null, info.getFormat());
    }
  }

  private void mergeFrom(@NotNull WCInfo info, @NotNull WorkingCopyInfoPanel infoPanel) {
    VirtualFile root = info.getRootInfo().getVirtualFile();
    if (!checkRoot(root, " invoke Merge From")) return;

    SelectBranchPopup.showForBranchRoot(myProject, root, (project, configuration, branchUrl, revision) -> {
      try {
        Url workingCopyUrlInSelectedBranch = getCorrespondingUrlInOtherBranch(configuration, info.getUrl(), branchUrl);
        MergeContext mergeContext = new MergeContext(getVcs(), workingCopyUrlInSelectedBranch, info, branchUrl.getTail(), root);

        new QuickMerge(mergeContext, new QuickMergeInteractionImpl(mergeContext)).execute();
      }
      catch (SvnBindException e) {
        AbstractVcsHelper.getInstance(myProject).showError(e, "Merge from " + branchUrl.getTail());
      }
    }, "Select branch", infoPanel);
  }

  private void cleanup(@NotNull WCInfo info) {
    VirtualFile root = info.getRootInfo().getVirtualFile();
    if (!checkRoot(root, " invoke Cleanup")) return;

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

  private static class ErrorsFoundNotification extends Notification {

    private static final String FIX_ACTION = "FIX";
    private static final String TITLE = "";

    private ErrorsFoundNotification(@NotNull final Project project) {
      super(NOTIFICATION_GROUP.getDisplayId(), TITLE, getDescription(), NotificationType.ERROR, createListener(project));
    }

    private static NotificationListener.Adapter createListener(@NotNull final Project project) {
      return new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          if (FIX_ACTION.equals(event.getDescription())) {
            WorkingCopiesContent.show(project);
          }
        }
      };
    }

    private static String getDescription() {
      return SvnBundle.message("subversion.roots.detection.errors.found.description");
    }
  }
}
