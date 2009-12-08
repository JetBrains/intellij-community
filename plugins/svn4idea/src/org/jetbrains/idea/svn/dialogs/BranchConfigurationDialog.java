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

package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.branchConfig.InfoReliability;
import org.jetbrains.idea.svn.branchConfig.InfoStorage;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigManager;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
import org.tmatesoft.svn.core.SVNException;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class BranchConfigurationDialog extends DialogWrapper {
  private JPanel myTopPanel;
  private TextFieldWithBrowseButton myTrunkLocationTextField;
  private JList myLocationList;
  private JButton myAddButton;
  private JButton myRemoveButton;
  private final SvnBranchConfigManager mySvnBranchConfigManager;
  private final VirtualFile myRoot;

  public BranchConfigurationDialog(final Project project, final SvnBranchConfigurationNew configuration, final String rootUrl,
                                   final VirtualFile root) {
    super(project, true);
    myRoot = root;
    init();
    setTitle(SvnBundle.message("configure.branches.title"));

    if (configuration.getTrunkUrl() == null) {
      configuration.setTrunkUrl(rootUrl);
    }

    mySvnBranchConfigManager = SvnBranchConfigurationManager.getInstance(project).getSvnBranchConfigManager();

    myTrunkLocationTextField.setText(configuration.getTrunkUrl());
    myTrunkLocationTextField.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        try {
          SelectLocationDialog dlg = new SelectLocationDialog(project, myTrunkLocationTextField.getText());
          dlg.show();
          if (dlg.isOK()) {
            myTrunkLocationTextField.setText(dlg.getSelectedURL());
          }
        }
        catch (SVNException e1) {
          // can not parse url, do not know repository 
        }
      }
    });

    final TrunkUrlValidator trunkUrlValidator = new TrunkUrlValidator(rootUrl, configuration);
    myTrunkLocationTextField.getTextField().getDocument().addDocumentListener(trunkUrlValidator);
    trunkUrlValidator.textChanged(null);

    final MyListModel listModel = new MyListModel(configuration);
    myLocationList.setModel(listModel);
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        try {
          SelectLocationDialog dlg = new SelectLocationDialog(project, rootUrl);
          dlg.show();
          if (dlg.isOK()) {
            if (!configuration.getBranchUrls().contains(dlg.getSelectedURL())) {
              final String url = dlg.getSelectedURL();
              configuration.addBranches(url, new InfoStorage<List<SvnBranchItem>>(new ArrayList<SvnBranchItem>(), InfoReliability.empty));
              mySvnBranchConfigManager.reloadBranches(myRoot, url, null);
              listModel.fireItemAdded();
              myLocationList.setSelectedIndex(listModel.getSize()-1);
            }
          }
        }
        catch (SVNException e1) {
          Messages.showErrorDialog(project, SvnBundle.message("select.location.invalid.url.message", rootUrl),
                                   SvnBundle.message("dialog.title.select.repository.location"));
        }
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int selIndex = myLocationList.getSelectedIndex();
        Object[] selection = myLocationList.getSelectedValues();
        for(Object urlObj: selection) {
          String url = (String) urlObj;
          int index = configuration.getBranchUrls().indexOf(url);
          configuration.removeBranch(url);
          listModel.fireItemRemoved(index);
        }
        if (listModel.getSize() > 0) {
          if (selIndex >= listModel.getSize())
            selIndex = listModel.getSize()-1;
          myLocationList.setSelectedIndex(selIndex);
        }
      }
    });
  }

  private class TrunkUrlValidator extends DocumentAdapter {
    private final String myRootUrl;
    private final String myRootUrlPrefix;
    private final SvnBranchConfigurationNew myConfiguration;

    private TrunkUrlValidator(final String rootUrl, final SvnBranchConfigurationNew configuration) {
      myRootUrl = rootUrl;
      myRootUrlPrefix = rootUrl + "/";
      myConfiguration = configuration;
    }

    protected void textChanged(final DocumentEvent e) {
      final String currentValue = myTrunkLocationTextField.getText();
      final boolean valueOk = (currentValue != null) && (currentValue.equals(myRootUrl) || currentValue.startsWith(myRootUrlPrefix));
      final boolean prefixOk = (currentValue != null) && (currentValue.startsWith(myRootUrlPrefix)) &&
          (currentValue.length() > myRootUrlPrefix.length());

      myTrunkLocationTextField.getButton().setEnabled(valueOk);
      setOKActionEnabled(prefixOk);
      if (prefixOk) {
        myConfiguration.setTrunkUrl(currentValue.endsWith("/") ? currentValue.substring(0, currentValue.length() - 1) : currentValue);
      }
      setErrorText(prefixOk ? null : SvnBundle.message("configure.branches.error.wrong.url"));
    }
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myTopPanel;
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "Subversion.BranchConfigurationDialog";
  }

  public static void configureBranches(final Project project, final VirtualFile file) {
    configureBranches(project, file, false);
  }

  public static void configureBranches(final Project project, final VirtualFile file, final boolean isRoot) {
    final VirtualFile vcsRoot = (isRoot) ? file : ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file);
    if (vcsRoot == null) {
      return;
    }

    final VirtualFile directory = SvnUtil.correctRoot(project, file);
    if (directory == null) {
      return;
    }
    final RootUrlInfo wcRoot = SvnVcs.getInstance(project).getSvnFileUrlMapping().getWcRootForFilePath(new File(directory.getPath()));
    if (wcRoot == null) {
      return;
    }
    final String rootUrl = wcRoot.getRepositoryUrl();
    if (rootUrl == null) {
      Messages.showErrorDialog(project, SvnBundle.message("configure.branches.error.no.connection.title"),
                               SvnBundle.message("configure.branches.title"));
      return;
    }

    SvnBranchConfigurationNew configuration;
    try {
      configuration = SvnBranchConfigurationManager.getInstance(project).get(vcsRoot);
    }
    catch (VcsException ex) {
      Messages.showErrorDialog(project, "Error loading branch configuration: " + ex.getMessage(),
                               SvnBundle.message("configure.branches.title"));
      return;
    }

    final SvnBranchConfigurationNew clonedConfiguration = configuration.copy();
    BranchConfigurationDialog dlg = new BranchConfigurationDialog(project, clonedConfiguration, rootUrl, vcsRoot);
    dlg.show();
    if (dlg.isOK()) {
      SvnBranchConfigurationManager.getInstance(project).setConfiguration(vcsRoot, clonedConfiguration);
    }
  }

  private static class MyListModel extends AbstractListModel {
    private final SvnBranchConfigurationNew myConfiguration;

    public MyListModel(final SvnBranchConfigurationNew configuration) {
      myConfiguration = configuration;
    }

    public int getSize() {
      return myConfiguration.getBranchUrls().size();
    }

    public Object getElementAt(final int index) {
      return myConfiguration.getBranchUrls().get(index);
    }

    public void fireItemAdded() {
      final int index = myConfiguration.getBranchUrls().size() - 1;
      super.fireIntervalAdded(this, index, index);
    }

    public void fireItemRemoved(final int index) {
      super.fireIntervalRemoved(this, index, index);
    }
  }
}
