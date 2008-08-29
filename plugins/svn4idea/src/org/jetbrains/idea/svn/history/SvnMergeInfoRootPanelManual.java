package org.jetbrains.idea.svn.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.idea.svn.SvnBranchConfiguration;
import org.jetbrains.idea.svn.SvnBranchMapperManager;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.actions.SelectBranchPopup;
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
            IntegratedSelectedOptionsDialog.selectWorkingCopy(myProject, myInfo.getUrl(), mySelectedBranch.getUrl(), false);
          if (info != null) {
            final String local = info.getFirst().getLocalPath();
            myBranchToLocal.put(mySelectedBranch.getUrl(), local);
            myLocalArea.setText(local);
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
            public void branchSelected(final Project project, final SvnBranchConfiguration configuration, final String url, final long revision) {
              final String branch = SVNPathUtil.tail(url);
              myBranchField.setText(branch);
              calculateBranchPathByBranch(url);
            }
          }, SvnBundle.message("select.branch.popup.general.title"));
          myListener.run();
        }
      }
    });

    if (myInfo.getBranches().isEmpty()) {
      calculateBranchPathByBranch(null);
    } else {
      final WCInfoWithBranches.Branch branch = myInfo.getBranches().get(0);
      final String branchName = SVNPathUtil.tail(branch.getUrl());
      myBranchField.setText(branchName);
      calculateBranchPathByBranch(branch.getUrl());
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
    myUrlText.setBackground(new Color(236,233,216));
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
    myLocalArea.setBackground(new Color(236,233,216));
    myLocalArea.setLineWrap(true);
    myLocalArea.setWrapStyleWord(true);
    myContentPanel.add(myLocalArea, gb);

    ++ gb.gridx;
    gb.weightx = 0;
    gb.fill = GridBagConstraints.NONE;
    myFixedSelectLocal = new FixedSizeButton(20);
    myContentPanel.add(myFixedSelectLocal, gb);
  }

  private void calculateBranchPathByBranch(final String url) {
    final String cached = myBranchToLocal.get(url);
    if (cached != null) {
      myLocalArea.setText(cached);
      myLocalArea.setForeground(UIUtil.getInactiveTextColor());
      return;
    }
    final Set<String> paths = url == null ? null : SvnBranchMapperManager.getInstance().get(url);
    if ((paths == null) || (paths.isEmpty())) {
      myLocalArea.setForeground(Color.red);
      myLocalArea.setText(SvnBundle.message("tab.repository.merge.panel.root.panel.select.local"));
    } else {
      final java.util.List<String> list = new ArrayList<String>(paths);
      Collections.sort(list);
      myLocalArea.setForeground(UIUtil.getInactiveTextColor());
      myLocalArea.setText(list.get(0));
      myBranchToLocal.put(url, list.get(0));
    }
    if ((mySelectedBranch != null) && (mySelectedBranch.getUrl().equals(url))) {
      return;
    }
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

  private static final int CUT_SIZE = 20;
  private static String cutString(final String s, final char separator) {
    /*final String trimmed = s.trim();
    if (trimmed.length() <= CUT_SIZE) {
      return trimmed;
    }
    return "..." + trimmed.substring(trimmed.length() - CUT_SIZE + 3);*/
    final String[] parts = s.trim().replace(separator, '/').split("/");
    final StringBuilder sb = new StringBuilder();
    int curLength = 0;
    for (int i = 0; i < parts.length; i++) {
      final String part = parts[i];
      final int len = part.length();
      if ((curLength + len + 1) <= CUT_SIZE) {
        // this line
        if (i > 0) {
          sb.append(separator);
        }
        sb.append(part);
        curLength += len + 1;
      } else {
        if (i > 0) {
          sb.append(separator);
        }
        sb.append('\n');
        sb.append(part);
        curLength = len + 1;
      }
    }
    return sb.toString();
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
    mySelectedBranch = holder.getBranch();
    if (mySelectedBranch != null) {
      myBranchField.setText(SVNPathUtil.tail(mySelectedBranch.getUrl()));
      if (holder.getLocal() != null)
      myBranchToLocal.put(mySelectedBranch.getUrl(), holder.getLocal());
      calculateBranchPathByBranch(mySelectedBranch.getUrl());
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
