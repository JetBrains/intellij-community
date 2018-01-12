// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.branchConfig.SelectBranchPopup;
import org.jetbrains.idea.svn.branchConfig.SvnBranchMapperManager;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.integrate.IntegratedSelectedOptionsDialog;
import org.jetbrains.idea.svn.integrate.WorkingCopyInfo;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.Set;

public class SvnMergeInfoRootPanelManual {

  private JCheckBox myInclude;
  private TextFieldWithBrowseButton myBranchField;
  private FixedSizeButton myFixedSelectLocal;
  private JPanel myContentPanel;
  private JTextArea myUrlText;
  private JTextArea myLocalArea;
  private JTextArea myMixedRevisions;

  @NotNull private final Project myProject;
  @NotNull private final NotNullFunction<WCInfoWithBranches, WCInfoWithBranches> myRefresher;
  @NotNull private final Runnable myListener;
  private boolean myOnlyOneRoot;
  @NotNull private WCInfoWithBranches myInfo;
  @NotNull private final Map<String, String> myBranchToLocal;
  private WCInfoWithBranches.Branch mySelectedBranch;

  public SvnMergeInfoRootPanelManual(@NotNull Project project,
                                     @NotNull NotNullFunction<WCInfoWithBranches, WCInfoWithBranches> refresher,
                                     @NotNull Runnable listener,
                                     boolean onlyOneRoot,
                                     @NotNull WCInfoWithBranches info) {
    myOnlyOneRoot = onlyOneRoot;
    myInfo = info;
    myProject = project;
    myRefresher = refresher;
    myListener = listener;
    myBranchToLocal = ContainerUtil.newHashMap();

    init();
    myInclude.setVisible(!onlyOneRoot);
    initWithData();
  }

  private void initWithData() {
    myInclude.addActionListener(e -> myListener.run());
    myUrlText.setText(myInfo.getUrl().toString());
    myFixedSelectLocal.addActionListener(e -> {
      if (mySelectedBranch != null) {
        Pair<WorkingCopyInfo, Url> info =
          IntegratedSelectedOptionsDialog.selectWorkingCopy(myProject, myInfo.getUrl(), mySelectedBranch.getUrl(), false, null, null);
        if (info != null) {
          calculateBranchPathByBranch(mySelectedBranch.getUrl(), info.getFirst().getLocalPath());
        }

        myListener.run();
      }
    });

    myBranchField.getTextField().setEditable(false);
    myBranchField.addActionListener(e -> {
      final VirtualFile vf = SvnUtil.getVirtualFile(myInfo.getPath());
      if (vf != null) {
        SelectBranchPopup.show(myProject, vf, (project, configuration, url, revision) -> {
          refreshSelectedBranch(new WCInfoWithBranches.Branch(url));
          calculateBranchPathByBranch(mySelectedBranch.getUrl(), null);
          myListener.run();
        }, SvnBundle.message("select.branch.popup.general.title"));
      }
    });

    if (myInfo.getBranches().isEmpty()) {
      calculateBranchPathByBranch(null, null);
    } else {
      refreshSelectedBranch(myInfo.getBranches().get(0));
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
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBUI.insets(1), 0, 0);

    myInclude = new JCheckBox();
    gb.fill = GridBagConstraints.NONE;
    gb.weightx = 0;
    myContentPanel.add(myInclude, gb);

    // newline
    JLabel hereLabel = new JLabel("From:");
    ++ gb.gridy;
    gb.gridx = 0;
    myContentPanel.add(hereLabel, gb);

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
    JLabel thereLabel = new JLabel("To:");
    gb.weightx = 0;
    gb.gridwidth = 1;
    ++ gb.gridy;
    gb.gridx = 0;
    myContentPanel.add(thereLabel, gb);

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
    myMixedRevisions.setForeground(JBColor.RED);
    myMixedRevisions.setBackground(myContentPanel.getBackground());
    myContentPanel.add(myMixedRevisions, gb);

    myMixedRevisions.setVisible(false);
  }

  public void setMixedRevisions(final boolean value) {
    myMixedRevisions.setVisible(value);
  }

  @Nullable
  private static String getLocal(@NotNull String url, @Nullable String localPath) {
    String result = null;
    Set<String> paths = SvnBranchMapperManager.getInstance().get(url);

    if (!ContainerUtil.isEmpty(paths)) {
      result = localPath != null ? ContainerUtil.find(paths, localPath) : ContainerUtil.getFirstItem(ContainerUtil.sorted(paths));
    }

    return result;
  }

  // always assign to local area here
  private void calculateBranchPathByBranch(@Nullable String url, @Nullable String localPath) {
    final String local = url == null ? null : getLocal(url, localPath == null ? myBranchToLocal.get(url) : localPath);
    if (local == null) {
      myLocalArea.setForeground(JBColor.RED);
      myLocalArea.setText(SvnBundle.message("tab.repository.merge.panel.root.panel.select.local"));
    } else {
      myLocalArea.setForeground(UIUtil.getInactiveTextColor());
      myLocalArea.setText(local);
      myBranchToLocal.put(url, local);
    }
  }

  // always assign to selected branch here
  private void refreshSelectedBranch(@NotNull WCInfoWithBranches.Branch branch) {
    myBranchField.setText(branch.getName());

    if (!initSelectedBranch(branch)) {
      myInfo = myRefresher.fun(myInfo);
      initSelectedBranch(branch);
    }
  }

  private boolean initSelectedBranch(@NotNull WCInfoWithBranches.Branch branch) {
    boolean found = myInfo.getBranches().contains(branch);

    if (found) {
      mySelectedBranch = branch;
    }

    return found;
  }

  public void setOnlyOneRoot(final boolean onlyOneRoot) {
    myOnlyOneRoot = onlyOneRoot;
    myInclude.setEnabled(! myOnlyOneRoot);
    myInclude.setSelected(true);
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }

  private void createUIComponents() {
    myFixedSelectLocal = new FixedSizeButton(20);
  }

  @NotNull
  public InfoHolder getInfo() {
    return new InfoHolder(mySelectedBranch, getLocalBranch(), myInclude.isSelected());
  }

  public void initSelection(@NotNull InfoHolder holder) {
    myInclude.setSelected(holder.isEnabled());
    if (holder.getBranch() != null) {
      refreshSelectedBranch(holder.getBranch());
      calculateBranchPathByBranch(mySelectedBranch.getUrl(), holder.getLocal());
    }
  }

  public static class InfoHolder {

    @Nullable private final WCInfoWithBranches.Branch myBranch;
    @Nullable private final String myLocal;
    private final boolean myEnabled;

    public InfoHolder(@Nullable WCInfoWithBranches.Branch branch, @Nullable String local, boolean enabled) {
      myBranch = branch;
      myLocal = local;
      myEnabled = enabled;
    }

    @Nullable
    public WCInfoWithBranches.Branch getBranch() {
      return myBranch;
    }

    @Nullable
    public String getLocal() {
      return myLocal;
    }

    public boolean isEnabled() {
      return myEnabled;
    }
  }

  @NotNull
  public WCInfoWithBranches getWcInfo() {
    return myInfo;
  }

  @Nullable
  public WCInfoWithBranches.Branch getBranch() {
    return mySelectedBranch;
  }

  @Nullable
  public String getLocalBranch() {
    return mySelectedBranch != null ? myBranchToLocal.get(mySelectedBranch.getUrl()) : null;
  }

  public boolean isEnabled() {
    return myOnlyOneRoot || myInclude.isSelected();
  }
}
