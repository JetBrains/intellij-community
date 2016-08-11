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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.ide.DataManager;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.DottedBorder;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.io.EqualityPolicy;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.actions.CleanupWorker;
import org.jetbrains.idea.svn.api.ClientFactory;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.branchConfig.BranchConfigurationDialog;
import org.jetbrains.idea.svn.branchConfig.SelectBranchPopup;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.jetbrains.idea.svn.integrate.MergeContext;
import org.jetbrains.idea.svn.integrate.QuickMerge;
import org.jetbrains.idea.svn.integrate.QuickMergeInteractionImpl;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.*;
import java.util.List;

import static com.intellij.notification.NotificationDisplayType.STICKY_BALLOON;

public class CopiesPanel {

  private static final Logger LOG = Logger.getInstance(CopiesPanel.class);

  private static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup("Svn Roots Detection Errors", STICKY_BALLOON, true);

  private final Project myProject;
  private MessageBusConnection myConnection;
  private SvnVcs myVcs;
  private JPanel myPanel;
  private JComponent myHolder;
  private LinkLabel myRefreshLabel;
  // updated only on AWT
  private List<OverrideEqualsWrapper<WCInfo>> myCurrentInfoList;
  private int myTextHeight;

  private final static String CHANGE_FORMAT = "CHANGE_FORMAT";
  private final static String CLEANUP = "CLEANUP";
  private final static String FIX_DEPTH = "FIX_DEPTH";
  private final static String CONFIGURE_BRANCHES = "CONFIGURE_BRANCHES";
  private final static String MERGE_FROM = "MERGE_FROM";

  public CopiesPanel(final Project project) {
    myProject = project;
    myConnection = myProject.getMessageBus().connect(myProject);
    myVcs = SvnVcs.getInstance(myProject);
    myCurrentInfoList = null;

    final Runnable focus = new Runnable() {
      @Override
      public void run() {
        IdeFocusManager.getInstance(myProject).requestFocus(myRefreshLabel, true);
      }
    };
    final Runnable refreshView = new Runnable() {
      @Override
      public void run() {
        final List<WCInfo> infoList = myVcs.getWcInfosWithErrors();
        final boolean hasErrors = !myVcs.getSvnFileUrlMapping().getErrorRoots().isEmpty();
        final List<WorkingCopyFormat> supportedFormats = getSupportedFormats();
        Runnable runnable = new Runnable() {
          @Override
          public void run() {
            if (myCurrentInfoList != null) {
              final List<OverrideEqualsWrapper<WCInfo>> newList =
                ObjectsConvertor.convert(infoList, new Convertor<WCInfo, OverrideEqualsWrapper<WCInfo>>() {
                  @Override
                  public OverrideEqualsWrapper<WCInfo> convert(WCInfo o) {
                    return new OverrideEqualsWrapper<>(InfoEqualityPolicy.getInstance(), o);
                  }
                }, ObjectsConvertor.NOT_NULL);

              if (Comparing.haveEqualElements(newList, myCurrentInfoList)) {
                myRefreshLabel.setEnabled(true);
                return;
              }
              myCurrentInfoList = newList;
            }
            Collections.sort(infoList, WCComparator.getInstance());
            updateList(infoList, supportedFormats);
            myRefreshLabel.setEnabled(true);
            showErrorNotification(hasErrors);
            SwingUtilities.invokeLater(focus);
          }
        };
        ApplicationManager.getApplication().invokeLater(runnable, ModalityState.NON_MODAL);
      }
    };
    final Consumer<Boolean> refreshOnPooled = new Consumer<Boolean>() {
      @Override
      public void consume(Boolean somethingNew) {
        if (Boolean.TRUE.equals(somethingNew)) {
          if (ApplicationManager.getApplication().isUnitTestMode()) {
            refreshView.run();
          }
          else {
            ApplicationManager.getApplication().executeOnPooledThread(refreshView);
          }
        } else {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              myRefreshLabel.setEnabled(true);
            }
          }, ModalityState.NON_MODAL);
        }
      }
    };
    myConnection.subscribe(SvnVcs.ROOTS_RELOADED, refreshOnPooled);

    final JPanel holderPanel = new JPanel(new BorderLayout());
    FontMetrics fm = holderPanel.getFontMetrics(holderPanel.getFont());
    myTextHeight = (int)(fm.getHeight() * 1.3);
    myPanel = new JPanel(new GridBagLayout());
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(myPanel, BorderLayout.NORTH);
    holderPanel.add(panel, BorderLayout.WEST);
    myRefreshLabel = new MyLinkLabel(myTextHeight, "Refresh", new LinkListener() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        if (myRefreshLabel.isEnabled()) {
          myVcs.invokeRefreshSvnRoots();
          myRefreshLabel.setEnabled(false);
        }
      }
    });
    final JScrollPane pane = ScrollPaneFactory.createScrollPane(holderPanel);
    registerHelp(pane);
    myHolder = pane;
    final JScrollBar vBar = pane.getVerticalScrollBar();
    vBar.setBlockIncrement(vBar.getBlockIncrement() * 5);
    vBar.setUnitIncrement(vBar.getUnitIncrement() * 5);
    myHolder.setBorder(null);
    setFocusableForLinks(myRefreshLabel);
    refreshOnPooled.consume(true);
    initView();
  }

  public JComponent getPreferredFocusedComponent() {
    return myRefreshLabel;
  }

  private void updateList(@NotNull final List<WCInfo> infoList, @NotNull final List<WorkingCopyFormat> supportedFormats) {
    myPanel.removeAll();
    final Insets nullIndent = new Insets(1, 3, 1, 0);
    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(2, 2, 0, 0), 0, 0);
    gb.insets.left = 4;
    myPanel.add(myRefreshLabel, gb);
    gb.insets.left = 1;

    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final Insets topIndent = new Insets(10, 3, 0, 0);
    for (final WCInfo wcInfo : infoList) {
      final Collection<WorkingCopyFormat> upgradeFormats = getUpgradeFormats(wcInfo, supportedFormats);

      final VirtualFile vf = lfs.refreshAndFindFileByIoFile(new File(wcInfo.getPath()));
      final VirtualFile root = (vf == null) ? wcInfo.getVcsRoot() : vf;

      final JEditorPane editorPane = new JEditorPane(UIUtil.HTML_MIME, "");
      editorPane.setEditable(false);
      editorPane.setFocusable(true);
      editorPane.setBackground(UIUtil.getPanelBackground());
      editorPane.setOpaque(false);
      editorPane.addHyperlinkListener(new HyperlinkListener() {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent e) {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            if (CONFIGURE_BRANCHES.equals(e.getDescription())) {
              if (! checkRoot(root, wcInfo.getPath(), " invoke Configure Branches")) return;
              BranchConfigurationDialog.configureBranches(myProject, root);
            } else if (FIX_DEPTH.equals(e.getDescription())) {
              final int result =
                Messages.showOkCancelDialog(myVcs.getProject(), "You are going to checkout into '" + wcInfo.getPath() + "' with 'infinity' depth.\n" +
                                                        "This will update your working copy to HEAD revision as well.",
                                    "Set Working Copy Infinity Depth",
                                    Messages.getWarningIcon());
              if (result == Messages.OK) {
                // update of view will be triggered by roots changed event
                SvnCheckoutProvider.checkout(myVcs.getProject(), new File(wcInfo.getPath()), wcInfo.getRootUrl(), SVNRevision.HEAD,
                                             Depth.INFINITY, false, null, wcInfo.getFormat());
              }
            } else if (CHANGE_FORMAT.equals(e.getDescription())) {
              changeFormat(wcInfo, upgradeFormats);
            } else if (MERGE_FROM.equals(e.getDescription())) {
              if (! checkRoot(root, wcInfo.getPath(), " invoke Merge From")) return;
              mergeFrom(wcInfo, root, editorPane);
            } else if (CLEANUP.equals(e.getDescription())) {
              if (! checkRoot(root, wcInfo.getPath(), " invoke Cleanup")) return;
              new CleanupWorker(new VirtualFile[] {root}, myVcs.getProject(), "action.Subversion.cleanup.progress.title").execute();
            }
          }
        }

        private boolean checkRoot(VirtualFile root, final String path, final String actionName) {
          if (root == null) {
            Messages.showWarningDialog(myProject, "Invalid working copy root: " + path, "Can not " + actionName);
            return false;
          }
          return true;
        }
      });
      editorPane.setBorder(null);
      editorPane.setText(formatWc(wcInfo, upgradeFormats));

      final JPanel copyPanel = new JPanel(new GridBagLayout());

      final GridBagConstraints gb1 =
        new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, nullIndent, 0, 0);
      gb1.insets.top = 1;
      gb1.gridwidth = 3;

      gb.insets = topIndent;
      gb.fill = GridBagConstraints.HORIZONTAL;
      ++ gb.gridy;

      final JPanel contForCopy = new JPanel(new BorderLayout());
      contForCopy.add(copyPanel, BorderLayout.WEST);
      myPanel.add(contForCopy, gb);

      copyPanel.add(editorPane, gb1);
      gb1.insets = nullIndent;
    }

    myPanel.revalidate();
    myPanel.repaint();
  }

  @SuppressWarnings("MethodMayBeStatic")
  private String formatWc(@NotNull WCInfo info, @NotNull Collection<WorkingCopyFormat> upgradeFormats) {
    final StringBuilder sb = new StringBuilder().append("<html><head>").append(UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()))
      .append("</head><body><table bgColor=\"").append(ColorUtil.toHex(UIUtil.getPanelBackground())).append("\">");

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

      sb.append("</table></body></html>");
    }
    return sb.toString();
  }

  @NotNull
  private List<WorkingCopyFormat> getSupportedFormats() {
    List<WorkingCopyFormat> result = ContainerUtil.newArrayList();
    ClientFactory factory = myVcs.getFactory();
    ClientFactory otherFactory = myVcs.getOtherFactory(factory);

    try {
      result.addAll(factory.createUpgradeClient().getSupportedFormats());
      result.addAll(SvnFormatWorker.getOtherFactoryFormats(otherFactory));
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

  private void mergeFrom(@NotNull final WCInfo wcInfo, @NotNull final VirtualFile root, @Nullable final Component mergeLabel) {
    SelectBranchPopup.showForBranchRoot(myProject, root, new SelectBranchPopup.BranchSelectedCallback() {
      @Override
      public void branchSelected(Project project,
                                 @NotNull SvnBranchConfigurationNew configuration,
                                 @NotNull String branchUrl,
                                 long revision) {
        String workingCopyUrlInSelectedBranch = getCorrespondingUrlInOtherBranch(configuration, wcInfo.getUrl(), branchUrl);
        MergeContext mergeContext = new MergeContext(myVcs, workingCopyUrlInSelectedBranch, wcInfo, SVNPathUtil.tail(branchUrl), root);

        new QuickMerge(mergeContext).execute(new QuickMergeInteractionImpl(myProject));
      }
    }, "Select branch", mergeLabel);
  }

  @NotNull
  private static String getCorrespondingUrlInOtherBranch(@NotNull SvnBranchConfigurationNew configuration,
                                                         @NotNull SVNURL url,
                                                         @NotNull String otherBranchUrl) {
    return SVNPathUtil.append(otherBranchUrl, configuration.getRelativeUrl(url.toDecodedString()));
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void setFocusableForLinks(final LinkLabel label) {
    final Border border = new DottedBorder(new Insets(1,2,1,1), JBColor.BLACK);
    label.setFocusable(true);
    label.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        super.focusGained(e);
        label.setBorder(border);
      }

      @Override
      public void focusLost(FocusEvent e) {
        super.focusLost(e);
        label.setBorder(null);
      }
    });
    label.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          label.doClick();
        }
      }
    });
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
      ApplicationManager.getApplication().saveAll();
      final Task.Backgroundable task = new SvnFormatWorker(myProject, newFormat, wcInfo) {
        @Override
        public void onSuccess() {
          super.onSuccess();
          myRefreshLabel.doClick();
        }
      };
      ProgressManager.getInstance().run(task);
    }
  }

  private void initView() {
    myRefreshLabel.doClick();
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

  private static void registerHelp(@NotNull JComponent component) {
    DataManager.registerDataProvider(component, new DataProvider() {
      @Nullable
      @Override
      public Object getData(@NonNls String dataId) {
        if (PlatformDataKeys.HELP_ID.is(dataId)) {
          return "reference.vcs.svn.working.copies.information";
        }
        return null;
      }
    });
  }

  public JComponent getComponent() {
    return myHolder;
  }

  public static class OverrideEqualsWrapper<T> {
    private final EqualityPolicy<T> myPolicy;
    private final T myT;

    public OverrideEqualsWrapper(EqualityPolicy<T> policy, T t) {
      myPolicy = policy;
      myT = t;
    }

    public T getT() {
      return myT;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final OverrideEqualsWrapper<T> that = (OverrideEqualsWrapper<T>) o;

      return myPolicy.isEqual(myT, that.getT());
    }

    @Override
    public int hashCode() {
      return myPolicy.getHashCode(myT);
    }
  }

  private static class InfoEqualityPolicy implements EqualityPolicy<WCInfo> {
    private final static InfoEqualityPolicy ourInstance = new InfoEqualityPolicy();

    public static InfoEqualityPolicy getInstance() {
      return ourInstance;
    }

    private static class HashCodeBuilder {
      private int myCode;

      private HashCodeBuilder() {
        myCode = 0;
      }

      public void append(final Object o) {
        myCode = 31 * myCode + (o != null ? o.hashCode() : 0);
      }

      public int getCode() {
        return myCode;
      }
    }

    @Override
    public int getHashCode(WCInfo value) {
      final HashCodeBuilder builder = new HashCodeBuilder();
      builder.append(value.getPath());
      builder.append(value.getUrl());
      builder.append(value.getFormat());
      builder.append(value.getType());
      builder.append(value.getStickyDepth());

      return builder.getCode();
    }

    @Override
    public boolean isEqual(WCInfo val1, WCInfo val2) {
      if (val1 == val2) return true;
      if (val1 == null || val2 == null || val1.getClass() != val2.getClass()) return false;

      if (! Comparing.equal(val1.getFormat(), val2.getFormat())) return false;
      if (! Comparing.equal(val1.getPath(), val2.getPath())) return false;
      if (! Comparing.equal(val1.getStickyDepth(), val2.getStickyDepth())) return false;
      if (! Comparing.equal(val1.getType(), val2.getType())) return false;
      if (! Comparing.equal(val1.getUrl(), val2.getUrl())) return false;

      return true;
    }
  }

  private static class WCComparator implements Comparator<WCInfo> {
    private final static WCComparator ourComparator = new WCComparator();

    public static WCComparator getInstance() {
      return ourComparator;
    }

    @Override
    public int compare(WCInfo o1, WCInfo o2) {
      return o1.getPath().compareTo(o2.getPath());
    }
  }

  private static class MyLinkLabel extends LinkLabel {
    private final int myHeight;

    public MyLinkLabel(final int height, final String text, final LinkListener linkListener) {
      super(text, null, linkListener);
      myHeight = height;
    }

    @Override
    public Dimension getPreferredSize() {
      final Dimension preferredSize = super.getPreferredSize();
      return new Dimension(preferredSize.width, myHeight);
    }
  }

  private static class ErrorsFoundNotification extends Notification {

    private static final String FIX_ACTION = "FIX";
    private static final String TITLE = "";
    private static final String DESCRIPTION = SvnBundle.message("subversion.roots.detection.errors.found.description");

    private ErrorsFoundNotification(@NotNull final Project project) {
      super(NOTIFICATION_GROUP.getDisplayId(), TITLE, DESCRIPTION, NotificationType.ERROR, createListener(project));
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
  }
}
