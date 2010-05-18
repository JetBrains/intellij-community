/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vcs.changes.ui.ChangesViewBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DottedBorder;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Convertor;
import com.intellij.util.io.EqualityPolicy;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.NestedCopyType;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.actions.ChangeListsMergerFactory;
import org.jetbrains.idea.svn.actions.SelectBranchPopup;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.jetbrains.idea.svn.history.FirstInBranch;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.jetbrains.idea.svn.integrate.IMerger;
import org.jetbrains.idea.svn.integrate.MergerFactory;
import org.jetbrains.idea.svn.integrate.SvnIntegrateChangesTask;
import org.jetbrains.idea.svn.integrate.WorkingCopyInfo;
import org.jetbrains.idea.svn.mergeinfo.BranchInfo;
import org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class CopiesPanel {
  private final Project myProject;
  private MessageBusConnection myConnection;
  private SvnVcs myVcs;
  private JPanel myPanel;
  private JComponent myHolder;
  private LinkLabel myRefreshLabel;
  // updated only on AWT
  private List<OverrideEqualsWrapper<WCInfo>> myCurrentInfoList;

  public CopiesPanel(final Project project) {
    myProject = project;
    myConnection = myProject.getMessageBus().connect(myProject);
    myVcs = SvnVcs.getInstance(myProject);
    myCurrentInfoList = null;

    final Runnable focus = new Runnable() {
      public void run() {
        IdeFocusManager.getInstance(myProject).requestFocus(myRefreshLabel, true);
      }
    };
    final Runnable refreshView = new Runnable() {
      public void run() {
        final List<WCInfo> infoList = myVcs.getAllWcInfos();
        if (myCurrentInfoList != null) {
          final List<OverrideEqualsWrapper<WCInfo>> newList =
            ObjectsConvertor.convert(infoList, new Convertor<WCInfo, OverrideEqualsWrapper<WCInfo>>() {
              public OverrideEqualsWrapper<WCInfo> convert(WCInfo o) {
                return new OverrideEqualsWrapper<WCInfo>(InfoEqualityPolicy.getInstance(), o);
              }
            }, ObjectsConvertor.NOT_NULL);

          if (Comparing.haveEqualElements(newList, myCurrentInfoList)) {
            myRefreshLabel.setEnabled(true);
            return;
          }
          myCurrentInfoList = newList;
        }
        updateList(infoList);
        myRefreshLabel.setEnabled(true);
        SwingUtilities.invokeLater(focus);
      }
    };
    myConnection.subscribe(SvnVcs.ROOTS_RELOADED, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().invokeLater(refreshView, ModalityState.NON_MODAL);
      }
    });

    final JPanel holderPanel = new JPanel(new BorderLayout());
    myPanel = new JPanel(new GridBagLayout());
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(myPanel, BorderLayout.NORTH);
    holderPanel.add(panel, BorderLayout.WEST);
    myRefreshLabel = new LinkLabel("Refresh", null, new LinkListener() {
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        if (myRefreshLabel.isEnabled()) {
          myVcs.invokeRefreshSvnRoots(false);
          myRefreshLabel.setEnabled(false);
        }
      }
    });
    myHolder = new JScrollPane(holderPanel);
    setFocusableForLinks(myRefreshLabel);
    refreshView.run();
    initView();
  }

  public JComponent getPrefferedFocusComponent() {
    return myRefreshLabel;
  }

  private JTextField createField(final String text) {
    final JTextField field = new JTextField(text);
    field.setBackground(UIUtil.getPanelBackgound());
    field.setEditable(false);                               
    field.setBorder(null);
    field.setFocusable(false);
    field.setHorizontalAlignment(JTextField.RIGHT);
    field.setCaretPosition(0);
    return field;
  }

  private void updateList(final List<WCInfo> infoList) {
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

      final JTextField path = createField(wcInfo.getPath());
      copyPanel.add(path, gb1);
      path.setFont(path.getFont().deriveFont(Font.BOLD));

      gb1.insets = nullIndent;
      gb1.insets.top = 5;
      ++ gb1.gridy;
      final JTextField url = createField("URL: " + wcInfo.getRootUrl());
      copyPanel.add(url, gb1);

      ++ gb1.gridy;
      gb1.insets.top = 1;
      final JTextField format = createField("Format: " + wcInfo.getFormat().getName());
      copyPanel.add(format, gb1);

      ++ gb1.gridy;
      final JTextField depth = createField("Depth: " + wcInfo.getStickyDepth().getName());
      copyPanel.add(depth, gb1);

      if (! SVNDepth.INFINITY.equals(wcInfo.getStickyDepth())) {
        gb1.gridx = 2;
        final LinkLabel fixDepthLabel = new LinkLabel("Make infinity", null, new LinkListener() {
          public void linkSelected(LinkLabel aSource, Object aLinkData) {
            final int result =
              Messages.showDialog(myVcs.getProject(), "You are going to checkout into '" + wcInfo.getPath() + "' with 'infinity' depth.\n" +
                                                      "This will update your working copy to HEAD revision as well.",
                                  "Set working copy infinity depth",
                                  new String[]{"Ok", "Cancel"}, 0, Messages.getWarningIcon());
            if (result == 0) {
              // update of view will be triggered by roots changed event
              SvnCheckoutProvider.checkout(myVcs.getProject(), new File(wcInfo.getPath()), wcInfo.getRootUrl(), SVNRevision.HEAD,
                                             SVNDepth.INFINITY, false, null, wcInfo.getFormat());
            }
          }
        });
        copyPanel.add(fixDepthLabel, gb1);
        setFocusableForLinks(fixDepthLabel);
        gb1.gridx = 0;
      }

      final NestedCopyType type = wcInfo.getType();
      if (NestedCopyType.external.equals(type) || NestedCopyType.switched.equals(type)) {
        ++ gb1.gridy;
        final JTextField whetherNested = createField(type.getName() + " copy");
        copyPanel.add(whetherNested, gb1);
        whetherNested.setFont(whetherNested.getFont().deriveFont(Font.ITALIC));
      }
      if (wcInfo.isIsWcRoot()) {
        ++ gb1.gridy;
        final JTextField whetherRoot = createField("Working copy root");
        copyPanel.add(whetherRoot, gb1);
        whetherRoot.setFont(whetherRoot.getFont().deriveFont(Font.ITALIC));
      }

      gb1.gridwidth = 1;
      gb1.insets.top = 5;
      ++ gb1.gridy;
      final LinkLabel formatLabel = new LinkLabel("Change Format", null, new LinkListener() {
        public void linkSelected(LinkLabel aSource, Object aLinkData) {
          changeFormat(wcInfo);
        }
      });
      copyPanel.add(formatLabel, gb1);
      setFocusableForLinks(formatLabel);

      final VirtualFile vf = lfs.refreshAndFindFileByIoFile(new File(wcInfo.getPath()));
      final VirtualFile root = (vf == null) ? wcInfo.getVcsRoot() : vf;
      ++ gb1.gridx;
      final LinkLabel configureBranchesLabel = new LinkLabel("Configure Branches", null, new LinkListener() {
        public void linkSelected(LinkLabel aSource, Object aLinkData) {
          BranchConfigurationDialog.configureBranches(myProject, root, true);
        }
      });
      if (root == null) {
        configureBranchesLabel.setEnabled(false); //+-
      }
      copyPanel.add(configureBranchesLabel, gb1);
      setFocusableForLinks(configureBranchesLabel);

      ++ gb1.gridx;
      final LinkLabel mergeLabel = new LinkLabel("Merge from...", null);
      mergeLabel.setListener(new LinkListener() {
        public void linkSelected(LinkLabel aSource, Object aLinkData) {
          mergeFrom(wcInfo, root, mergeLabel);
        }
      }, null);
      if (root == null) {
        mergeLabel.setEnabled(false); //+-
      }
      copyPanel.add(mergeLabel, gb1);
      setFocusableForLinks(mergeLabel);
    }

    myPanel.revalidate();
    myPanel.repaint();
  }

  private void mergeFrom(final WCInfo wcInfo, final VirtualFile root, final LinkLabel mergeLabel) {
    SelectBranchPopup.showForBranchRoot(myProject, root, new SelectBranchPopup.BranchSelectedCallback() {
      public void branchSelected(Project project, SvnBranchConfigurationNew configuration, String url, long revision) {
        if (url.equals(wcInfo.getRootUrl())) {
          showErrorBalloon("Cannot merge from self");
          return;
        }
        ProgressManager.getInstance().run(new MergeCalculator(wcInfo, root, url, configuration.getBaseName(url)));
      }
    }, "Select branch", mergeLabel);
  }

  private void showErrorBalloon(final String s) {
    ChangesViewBalloonProblemNotifier.showMe(myProject, s, MessageType.ERROR);
  }

  private class MergeCalculator extends Task.Backgroundable {
    private final WCInfo myWcInfo;
    private final VirtualFile myRoot;
    private final String mySourceUrl;
    private final String myBranchName;
    private boolean myIsReintegrate;

    private final List<CommittedChangeList> myNotMerged;
    private String myMergeTitle;
    private BranchInfo myBranchInfo;

    private MergeCalculator(WCInfo wcInfo, VirtualFile root, String sourceUrl, String branchName) {
      super(CopiesPanel.this.myProject, "Calculating not merged revisions", true, BackgroundFromStartOption.getInstance());
      myWcInfo = wcInfo;
      myRoot = root;
      mySourceUrl = sourceUrl;
      myBranchName = branchName;
      myNotMerged = new LinkedList<CommittedChangeList>();
      myMergeTitle = "Merge from " + branchName;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      // branch is info holder
      new FirstInBranch(myVcs, myWcInfo.getRepositoryRoot(), myWcInfo.getRootUrl(), mySourceUrl,
                        new Consumer<FirstInBranch.CopyData>() {
                          public void consume(FirstInBranch.CopyData copyData) {
          if (copyData == null) {
            showErrorBalloon("Merge start wasn't found");
            return;
          }

          myIsReintegrate = ! copyData.isTrunkSupposedCorrect();
          if (! myWcInfo.getFormat().supportsMergeInfo()) return;
          final long localLatest = Math.max(copyData.getCopyTargetRevision(), copyData.getCopySourceRevision());
          myBranchInfo = new BranchInfo(myVcs, myWcInfo.getRepositoryRoot(), myWcInfo.getRootUrl(), mySourceUrl, mySourceUrl, myVcs.createWCClient());

          final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider =
            myVcs.getCommittedChangesProvider();
          final ChangeBrowserSettings settings = new ChangeBrowserSettings();
          settings.CHANGE_AFTER = Long.toString(localLatest);
          try {
            committedChangesProvider.loadCommittedChanges(settings, new SvnRepositoryLocation(mySourceUrl),
                                          committedChangesProvider.getUnlimitedCountValue(), new AsynchConsumer<CommittedChangeList>() {
                public void finished() {
                }

                public void consume(CommittedChangeList committedChangeList) {
                  final SvnChangeList svnList = (SvnChangeList)committedChangeList;
                  if (localLatest >= svnList.getNumber()) return;

                  final SvnMergeInfoCache.MergeCheckResult checkResult =
                    myBranchInfo.checkList(svnList, myWcInfo.getPath());
                  if (SvnMergeInfoCache.MergeCheckResult.NOT_MERGED.equals(checkResult)) {
                    myNotMerged.add(svnList);
                  }
                }
              });
          }
          catch (VcsException e) {
            AbstractVcsHelper.getInstance(myProject).showErrors(Collections.singletonList(e), "Checking revisions for merge fault");
          }
        }
      }).run();
    }

    @Override
    public void onCancel() {
      onSuccess();
    }

    @Nullable
    private MergerFactory askParameters() {
      final int result = Messages.showDialog(myProject, myNotMerged.size() + " not merged revision(s) found.", myMergeTitle,
                          new String[]{"Merge &all", "&Select revisions to merge", "Cancel"}, 0, Messages.getQuestionIcon());
      if (result == 2) return null;

      final MergerFactory factory;
      if (result == 0) {
        factory = createMergeAllFactory();
      } else {
        final ToBeMergedDialog dialog = new ToBeMergedDialog(myProject, myNotMerged, myMergeTitle, myBranchInfo);
        dialog.show();
        if (dialog.getExitCode() == DialogWrapper.CANCEL_EXIT_CODE) {
          return null;
        }
          final List<CommittedChangeList> lists = dialog.getSelected();
          if (lists.isEmpty()) return null;
          factory = new ChangeListsMergerFactory(lists);
      }
      return factory;
    }

    private MergerFactory createMergeAllFactory() {
      return new MergerFactory() {
        public IMerger createMerger(SvnVcs vcs, File target, UpdateEventHandler handler, SVNURL currentBranchUrl) {
          return new BranchMerger(vcs, currentBranchUrl, myWcInfo.getUrl(), myWcInfo.getPath(), handler, myIsReintegrate, myBranchName);
        }
      };
    }

    @Override
    public void onSuccess() {
      if (! myWcInfo.getFormat().supportsMergeInfo()) {
        doMerge(createMergeAllFactory());
        return;
      }
      if (myNotMerged.isEmpty()) {
        ChangesViewBalloonProblemNotifier.showMe(myProject, "Everything is up-to-date", MessageType.WARNING);
        return;
      }
      final MergerFactory factory = askParameters();
      if (factory == null) return;
      doMerge(factory);
    }

    private void doMerge(MergerFactory factory) {
      final SVNURL sourceUrlUrl;
      try {
        sourceUrlUrl = SVNURL.parseURIEncoded(mySourceUrl);
      } catch (SVNException e) {
        showErrorBalloon("Cannot merge: " + e.getMessage());
        return;
      }
      final SvnIntegrateChangesTask task = new SvnIntegrateChangesTask(SvnVcs.getInstance(myProject),
                                               new WorkingCopyInfo(myWcInfo.getPath(), true), factory, sourceUrlUrl, myMergeTitle, false);
      ProgressManager.getInstance().run(task);
    }
  }

  private void setFocusableForLinks(final LinkLabel label) {
    final Border border = new DottedBorder(new Insets(1,2,1,1), Color.black);
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

  private void changeFormat(final WCInfo wcInfo) {
    ChangeFormatDialog dialog = new ChangeFormatDialog(myProject, new File(wcInfo.getPath()), false, ! wcInfo.isIsWcRoot());
    dialog.setData(true, wcInfo.getFormat().getOption());
    dialog.show();
    if (! dialog.isOK()) {
      return;
    }
    final String newMode = dialog.getUpgradeMode();
    if (! wcInfo.getFormat().getOption().equals(newMode)) {
      final WorkingCopyFormat newFormat = WorkingCopyFormat.getInstance(newMode);
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

    public int getHashCode(WCInfo value) {
      final HashCodeBuilder builder = new HashCodeBuilder();
      builder.append(value.getPath());
      builder.append(value.getUrl());
      builder.append(value.getFormat());
      builder.append(value.getType());
      builder.append(value.getStickyDepth());

      return builder.getCode();
    }

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
}
