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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBranchConfiguration;
import org.jetbrains.idea.svn.SvnBranchMapperManager;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.actions.SelectBranchPopup;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.integrate.IntegratedSelectedOptionsDialog;
import org.jetbrains.idea.svn.integrate.WorkingCopyInfo;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class SvnMergeInfoRootPanelManual {
  private JCheckBox myInclude;
  private JLabel myHereLabel;
  private JLabel myThereLabel;
  private TextFieldWithBrowseButton myBranchField;
  private FixedSizeButton myFixedSelectLocal;
  private JPanel myContentPanel;
  private JTextArea myUrlText;
  private JTextArea myLocalArea;
  private JTextArea myMixedRevisions;

  private final Project myProject;
  private final NullableFunction<WCInfoWithBranches, WCInfoWithBranches> myRefresher;
  private final Runnable myListener;
  private boolean myOnlyOneRoot;
  private WCInfoWithBranches myInfo;
  private final Map<String, String> myBranchToLocal;
  private WCInfoWithBranches.Branch mySelectedBranch;

  public SvnMergeInfoRootPanelManual(final Project project, final NullableFunction<WCInfoWithBranches, WCInfoWithBranches> refresher,
                                     final Runnable listener,
                                     final boolean onlyOneRoot,
                                     final WCInfoWithBranches info) {
    myOnlyOneRoot = onlyOneRoot;
    myInfo = info;
    myProject = project;
    myRefresher = refresher;
    myListener = listener;
    myBranchToLocal = new HashMap<String, String>();

    init();
    myInclude.setVisible(! onlyOneRoot);
    initWithData();
  }

  private void initWithData() {
    myInclude.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myListener.run();
      }
    });

    final String path = myInfo.getPath();
    final String urlString = myInfo.getUrl().toString();
    myUrlText.setText(urlString);

    myFixedSelectLocal.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (mySelectedBranch != null) {
          final Pair<WorkingCopyInfo,SVNURL> info =
            IntegratedSelectedOptionsDialog.selectWorkingCopy(myProject, myInfo.getUrl(), mySelectedBranch.getUrl(), false, null,
                                                              null);
          if (info != null) {
            final String local = info.getFirst().getLocalPath();
            calculateBranchPathByBranch(mySelectedBranch.getUrl(), local);
          }
          myListener.run();
        }
      }
    });

    myBranchField.getTextField().setEditable(false);
    myBranchField.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final VirtualFile vf = SvnUtil.getVirtualFile(myInfo.getPath());
        if (vf != null) {
          SelectBranchPopup.show(myProject, vf, new SelectBranchPopup.BranchSelectedCallback() {
            public void branchSelected(final Project project, final SvnBranchConfigurationNew configuration, final String url, final long revision) {
              refreshSelectedBranch(url);
              calculateBranchPathByBranch(mySelectedBranch.getUrl(), null);
              myListener.run();
            }
          }, SvnBundle.message("select.branch.popup.general.title"));
        }
      }
    });

    if (myInfo.getBranches().isEmpty()) {
      calculateBranchPathByBranch(null, null);
    } else {
      final WCInfoWithBranches.Branch branch = myInfo.getBranches().get(0);
      refreshSelectedBranch(branch.getUrl());
      calculateBranchPathByBranch(mySelectedBranch.getUrl(), null);
    }
  }

  private void init() {
    myContentPanel = new JPanel(new GridBagLayout()) {
      @Override
      public void setBounds(final Rectangle r) {
        super.setBounds(r);
      }
    };
    myContentPanel.setMinimumSize(new Dimension(200, 100));

    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(1, 1, 1, 1), 0, 0);

    myInclude = new JCheckBox();
    gb.fill = GridBagConstraints.NONE;
    gb.weightx = 0;
    myContentPanel.add(myInclude, gb);

    // newline
    myHereLabel = new JLabel("From:");
    ++ gb.gridy;
    gb.gridx = 0;
    myContentPanel.add(myHereLabel, gb);

    myUrlText = new JTextArea();
    myUrlText.setLineWrap(true);
    myUrlText.setBackground(UIUtil.getLabelBackground());
    myUrlText.setWrapStyleWord(true);
    gb.weightx = 1;
    ++ gb.gridx;
    gb.gridwidth = 2;
    gb.fill = GridBagConstraints.HORIZONTAL;
    myContentPanel.add(myUrlText, gb);

    // newline
    gb.fill = GridBagConstraints.NONE;
    myThereLabel = new JLabel("To:");
    gb.weightx = 0;
    gb.gridwidth = 1;
    ++ gb.gridy;
    gb.gridx = 0;
    myContentPanel.add(myThereLabel, gb);

    myBranchField = new TextFieldWithBrowseButton();
    gb.weightx = 1;
    ++ gb.gridx;
    gb.gridwidth = 2;
    gb.fill = GridBagConstraints.HORIZONTAL;
    myContentPanel.add(myBranchField, gb);

    // newline
    gb.gridx = 1;
    ++ gb.gridy;
    gb.gridwidth = 1;
    myLocalArea = new JTextArea();
    myLocalArea.setBackground(UIUtil.getLabelBackground());
    myLocalArea.setLineWrap(true);
    myLocalArea.setWrapStyleWord(true);
    myContentPanel.add(myLocalArea, gb);

    ++ gb.gridx;
    gb.weightx = 0;
    gb.fill = GridBagConstraints.NONE;
    myFixedSelectLocal = new FixedSizeButton(20);
    myContentPanel.add(myFixedSelectLocal, gb);

    ++ gb.gridy;
    gb.gridx = 0;
    gb.gridwidth = 2;
    myMixedRevisions = new JTextArea("Mixed Revision Working Copy");
    myMixedRevisions.setForeground(Color.red);
    myMixedRevisions.setBackground(myContentPanel.getBackground());
    myContentPanel.add(myMixedRevisions, gb);

    myMixedRevisions.setVisible(false);
  }

  public void setMixedRevisions(final boolean value) {
    myMixedRevisions.setVisible(value);
  }

  @Nullable
  private String getLocal(@NotNull final String url, @Nullable final String localPath) {
    final Set<String> paths = SvnBranchMapperManager.getInstance().get(url);
    if (paths != null && (! paths.isEmpty())) {
      if (localPath != null) {
        // check whether it is still actual
        for (String path : paths) {
          if (path.equals(localPath)) {
            return path;
          }
        }
      } else {
        final java.util.List<String> list = new ArrayList<String>(paths);
        Collections.sort(list);
        return list.get(0);
      }
    }
    return null;
  }

  // always assign to local area here
  private void calculateBranchPathByBranch(final String url, final String localPath) {
    final String local = url == null ? null : getLocal(url, localPath == null ? myBranchToLocal.get(url) : localPath);
    if (local == null) {
      myLocalArea.setForeground(Color.red);
      myLocalArea.setText(SvnBundle.message("tab.repository.merge.panel.root.panel.select.local"));
    } else {
      myLocalArea.setForeground(UIUtil.getInactiveTextColor());
      myLocalArea.setText(local);
      myBranchToLocal.put(url, local);
    }
  }

  // always assign to selected branch here
  private void refreshSelectedBranch(final String url) {
    final String branch = SVNPathUtil.tail(url);
    myBranchField.setText(branch);

    if (initSelectedBranch(url)) return;
    myInfo = myRefresher.fun(myInfo);
    initSelectedBranch(url);
  }

  private boolean initSelectedBranch(final String branch) {
    for (WCInfoWithBranches.Branch item : myInfo.getBranches()) {
      if (item.getUrl().equals(branch)) {
        mySelectedBranch = item;
        return true;
      }
    }
    return false;
  }

  public void setDirection(final boolean fromHere) {
    if (fromHere) {
      myHereLabel.setText(SvnBundle.message("tab.repository.merge.panel.root.panel.from"));
      myThereLabel.setText(SvnBundle.message("tab.repository.merge.panel.root.panel.to"));
    } else {
      myThereLabel.setText(SvnBundle.message("tab.repository.merge.panel.root.panel.from"));
      myHereLabel.setText(SvnBundle.message("tab.repository.merge.panel.root.panel.to"));
    }
  }

  public void setOnlyOneRoot(final boolean onlyOneRoot) {
    myOnlyOneRoot = onlyOneRoot;
    myInclude.setEnabled(! myOnlyOneRoot);
    myInclude.setSelected(true);
  }

  public void selectCheckbox(final boolean select) {
    myInclude.setSelected(select);
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }

  private void createUIComponents() {
    myFixedSelectLocal = new FixedSizeButton(20);
  }

  public InfoHolder getInfo() {
    return new InfoHolder(mySelectedBranch, getLocalBranch(), myInclude.isSelected());
  }

  public void initSelection(final InfoHolder holder) {
    myInclude.setSelected(holder.isEnabled());
    if (holder.getBranch() != null) {
      refreshSelectedBranch(holder.getBranch().getUrl());
      calculateBranchPathByBranch(mySelectedBranch.getUrl(), holder.getLocal());
    }
  }

  public static class InfoHolder {
    private final WCInfoWithBranches.Branch myBranch;
    private final String myLocal;
    private final boolean myEnabled;

    public InfoHolder(final WCInfoWithBranches.Branch branch, final String local, final boolean enabled) {
      myBranch = branch;
      myLocal = local;
      myEnabled = enabled;
    }

    public WCInfoWithBranches.Branch getBranch() {
      return myBranch;
    }

    public String getLocal() {
      return myLocal;
    }

    public boolean isEnabled() {
      return myEnabled;
    }
  }

  public WCInfoWithBranches getWcInfo() {
    return myInfo;
  }

  public WCInfoWithBranches.Branch getBranch() {
    return mySelectedBranch;
  }

  public String getLocalBranch() {
    if (mySelectedBranch != null) {
      return myBranchToLocal.get(mySelectedBranch.getUrl());
    }
    return null;
  }

  public boolean isEnabled() {
    return myOnlyOneRoot || myInclude.isSelected();
  }
}
