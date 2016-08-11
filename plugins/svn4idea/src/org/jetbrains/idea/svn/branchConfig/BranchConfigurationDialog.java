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

package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.SelectLocationDialog;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class BranchConfigurationDialog extends DialogWrapper {
  private JPanel myTopPanel;
  private TextFieldWithBrowseButton myTrunkLocationTextField;
  private JList myLocationList;
  private JPanel myListPanel;
  private JLabel myErrorPrompt;
  private final NewRootBunch mySvnBranchConfigManager;
  private final VirtualFile myRoot;

  public BranchConfigurationDialog(@NotNull final Project project,
                                   @NotNull final SvnBranchConfigurationNew configuration,
                                   final @NotNull SVNURL rootUrl,
                                   @NotNull final VirtualFile root,
                                   @NotNull String url) {
    super(project, true);
    myRoot = root;
    init();
    setTitle(SvnBundle.message("configure.branches.title"));

    final String trunkUrl = configuration.getTrunkUrl();
    if (trunkUrl == null || trunkUrl.trim().length() == 0) {
      configuration.setTrunkUrl(url);
    }

    mySvnBranchConfigManager = SvnBranchConfigurationManager.getInstance(project).getSvnBranchConfigManager();

    myTrunkLocationTextField.setText(configuration.getTrunkUrl());
    myTrunkLocationTextField.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        Pair<SVNURL, SVNURL> selectionData = SelectLocationDialog.selectLocation(project, rootUrl);

        if (selectionData != null && selectionData.getFirst() != null) {
          myTrunkLocationTextField.setText(selectionData.getFirst().toString());
        }
      }
    });

    final TrunkUrlValidator trunkUrlValidator = new TrunkUrlValidator(rootUrl, configuration);
    myTrunkLocationTextField.getTextField().getDocument().addDocumentListener(trunkUrlValidator);
    trunkUrlValidator.textChanged(null);

    myErrorPrompt.setUI(new MultiLineLabelUI());
    myErrorPrompt.setForeground(SimpleTextAttributes.ERROR_ATTRIBUTES.getFgColor());

    final MyListModel listModel = new MyListModel(configuration);
    myLocationList = new JBList(listModel);

    myListPanel.add(
      ToolbarDecorator.createDecorator(myLocationList)
        .setAddAction(new AnActionButtonRunnable() {

          @Nullable private SVNURL usedRootUrl;

          @Override
          public void run(AnActionButton button) {
            Pair<SVNURL, SVNURL> result = SelectLocationDialog.selectLocation(project, ObjectUtils.notNull(usedRootUrl, rootUrl));
            if (result != null) {
              String selectedUrl = result.getFirst().toString();
              usedRootUrl = result.getSecond();
              if (selectedUrl != null) {
                if (!configuration.getBranchUrls().contains(selectedUrl)) {
                  configuration
                    .addBranches(selectedUrl, new InfoStorage<>(new ArrayList<>(), InfoReliability.empty));
                  mySvnBranchConfigManager.reloadBranchesAsync(myRoot, selectedUrl, InfoReliability.setByUser);
                  listModel.fireItemAdded();
                  myLocationList.setSelectedIndex(listModel.getSize() - 1);
                }
              }
            }
          }
        }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          int selIndex = myLocationList.getSelectedIndex();
          Object[] selection = myLocationList.getSelectedValues();
          for (Object urlObj : selection) {
            String url = (String)urlObj;
            int index = configuration.getBranchUrls().indexOf(url);
            configuration.removeBranch(url);
            listModel.fireItemRemoved(index);
          }
          if (listModel.getSize() > 0) {
            if (selIndex >= listModel.getSize())
              selIndex = listModel.getSize() - 1;
            myLocationList.setSelectedIndex(selIndex);
          }
        }
      }).disableUpDownActions().setToolbarPosition(ActionToolbarPosition.BOTTOM).createPanel(), BorderLayout.CENTER);
  }

  private class TrunkUrlValidator extends DocumentAdapter {
    private final SVNURL myRootUrl;
    private final SvnBranchConfigurationNew myConfiguration;

    private TrunkUrlValidator(final SVNURL rootUrl, final SvnBranchConfigurationNew configuration) {
      myRootUrl = rootUrl;
      myConfiguration = configuration;
    }

    protected void textChanged(final DocumentEvent e) {
      SVNURL url = parseUrl(myTrunkLocationTextField.getText());

      if (url != null) {
        boolean isAncestor = SVNURLUtil.isAncestor(myRootUrl, url);
        boolean areNotSame = isAncestor && !url.equals(myRootUrl);

        if (areNotSame) {
          myConfiguration.setTrunkUrl(url.toDecodedString());
        }
        myErrorPrompt.setText(areNotSame ? "" : SvnBundle.message("configure.branches.error.wrong.url", myRootUrl));
      }
    }

    @Nullable
    private SVNURL parseUrl(@NotNull String url) {
      SVNURL result = null;

      try {
        result = SvnUtil.createUrl(url);
      }
      catch (SvnBindException e) {
        myErrorPrompt.setText(e.getMessage());
      }

      return result;
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

  public static void configureBranches(final Project project, @Nullable VirtualFile file) {
    if (file == null) {
      return;
    }

    final RootUrlInfo wcRoot = SvnVcs.getInstance(project).getSvnFileUrlMapping().getWcRootForFilePath(VfsUtilCore.virtualToIoFile(file));
    if (wcRoot == null) {
      return;
    }

    SvnBranchConfigurationNew configuration = SvnBranchConfigurationManager.getInstance(project).get(file);
    SvnBranchConfigurationNew clonedConfiguration = configuration.copy();
    BranchConfigurationDialog dlg =
      new BranchConfigurationDialog(project, clonedConfiguration, wcRoot.getRepositoryUrlUrl(), file, wcRoot.getUrl());
    if (dlg.showAndGet()) {
      SvnBranchConfigurationManager.getInstance(project).setConfiguration(file, clonedConfiguration);
    }
  }

  private static class MyListModel extends AbstractListModel {
    private final SvnBranchConfigurationNew myConfiguration;
    private List<String> myBranchUrls;

    public MyListModel(final SvnBranchConfigurationNew configuration) {
      myConfiguration = configuration;
      myBranchUrls = myConfiguration.getBranchUrls();
    }

    public int getSize() {
      return myBranchUrls.size();
    }

    public Object getElementAt(final int index) {
      return myBranchUrls.get(index);
    }

    public void fireItemAdded() {
      final int index = myConfiguration.getBranchUrls().size() - 1;
      myBranchUrls = myConfiguration.getBranchUrls();
      super.fireIntervalAdded(this, index, index);
    }

    public void fireItemRemoved(final int index) {
      myBranchUrls = myConfiguration.getBranchUrls();
      super.fireIntervalRemoved(this, index, index);
    }
  }
}
