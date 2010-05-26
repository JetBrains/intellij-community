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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DottedBorder;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.containers.Convertor;
import com.intellij.util.io.EqualityPolicy;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.idea.svn.NestedCopyType;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.actions.SelectBranchPopup;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.tmatesoft.svn.core.SVNDepth;
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
import java.util.Comparator;
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
        Collections.sort(infoList, WCComparator.getInstance());
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
        new QuickMerge(project, url, wcInfo, configuration.getBaseName(url), configuration, root).execute();
      }
    }, "Select branch", mergeLabel);
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

  private static class WCComparator implements Comparator<WCInfo> {
    private final static WCComparator ourComparator = new WCComparator();

    public static WCComparator getInstance() {
      return ourComparator;
    }

    public int compare(WCInfo o1, WCInfo o2) {
      return o1.getPath().compareTo(o2.getPath());
    }
  }
}
