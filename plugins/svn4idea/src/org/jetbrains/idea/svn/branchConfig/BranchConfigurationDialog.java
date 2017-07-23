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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ObjectUtils.notNull;
import static java.lang.Math.min;
import static org.jetbrains.idea.svn.dialogs.SelectLocationDialog.selectLocation;

public class BranchConfigurationDialog extends DialogWrapper {
  private JPanel myTopPanel;
  private TextFieldWithBrowseButton myTrunkLocationTextField;
  private JBList<String> myBranchLocationsList;
  @NotNull private final MyListModel myBranchLocationsModel;
  private JPanel myListPanel;
  private JLabel myErrorPrompt;
  @NotNull private final NewRootBunch mySvnBranchConfigManager;
  @NotNull private final VirtualFile myRoot;

  public BranchConfigurationDialog(@NotNull Project project,
                                   @NotNull SvnBranchConfigurationNew configuration,
                                   @NotNull SVNURL rootUrl,
                                   @NotNull VirtualFile root,
                                   @NotNull String url) {
    super(project, true);
    myRoot = root;
    init();
    setTitle(SvnBundle.message("configure.branches.title"));

    if (isEmptyOrSpaces(configuration.getTrunkUrl())) {
      configuration.setTrunkUrl(url);
    }

    mySvnBranchConfigManager = SvnBranchConfigurationManager.getInstance(project).getSvnBranchConfigManager();

    myTrunkLocationTextField.setText(configuration.getTrunkUrl());
    myTrunkLocationTextField.addActionListener(e -> {
      Pair<SVNURL, SVNURL> selectionData = selectLocation(project, rootUrl);

      if (selectionData != null && selectionData.first != null) {
        myTrunkLocationTextField.setText(selectionData.first.toString());
      }
    });

    TrunkUrlValidator trunkUrlValidator = new TrunkUrlValidator(rootUrl, configuration);
    myTrunkLocationTextField.getTextField().getDocument().addDocumentListener(trunkUrlValidator);
    trunkUrlValidator.textChanged(null);

    myErrorPrompt.setUI(new MultiLineLabelUI());
    myErrorPrompt.setForeground(SimpleTextAttributes.ERROR_ATTRIBUTES.getFgColor());

    myBranchLocationsModel = new MyListModel(configuration);
    myBranchLocationsList = new JBList<>(myBranchLocationsModel);

    myListPanel.add(wrapLocationsWithToolbar(project, rootUrl), BorderLayout.CENTER);
  }

  @NotNull
  private JPanel wrapLocationsWithToolbar(@NotNull Project project, @NotNull SVNURL rootUrl) {
    return ToolbarDecorator.createDecorator(myBranchLocationsList)
      .setAddAction(new AnActionButtonRunnable() {

        @Nullable private SVNURL usedRootUrl;

        @Override
        public void run(AnActionButton button) {
          Pair<SVNURL, SVNURL> result = selectLocation(project, notNull(usedRootUrl, rootUrl));
          if (result != null) {
            SVNURL selectedUrl = result.first;
            usedRootUrl = result.second;
            if (selectedUrl != null) {
              String selectedUrlValue = selectedUrl.toString();
              if (!myBranchLocationsModel.getConfiguration().getBranchUrls().contains(selectedUrlValue)) {
                myBranchLocationsModel.getConfiguration()
                  .addBranches(selectedUrlValue, new InfoStorage<>(new ArrayList<>(), InfoReliability.empty));
                mySvnBranchConfigManager.reloadBranchesAsync(myRoot, selectedUrlValue, InfoReliability.setByUser);
                myBranchLocationsModel.fireItemAdded();
                myBranchLocationsList.setSelectedIndex(myBranchLocationsModel.getSize() - 1);
              }
            }
          }
        }
      })
      .setRemoveAction(button -> {
        int selectedIndex = myBranchLocationsList.getSelectedIndex();
        for (String url : myBranchLocationsList.getSelectedValuesList()) {
          int index = myBranchLocationsModel.getConfiguration().getBranchUrls().indexOf(url);
          myBranchLocationsModel.getConfiguration().removeBranch(url);
          myBranchLocationsModel.fireItemRemoved(index);
        }
        if (myBranchLocationsModel.getSize() > 0) {
          selectedIndex = min(selectedIndex, myBranchLocationsModel.getSize() - 1);
          myBranchLocationsList.setSelectedIndex(selectedIndex);
        }
      })
      .disableUpDownActions()
      .setToolbarPosition(ActionToolbarPosition.BOTTOM)
      .createPanel();
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

  public static void configureBranches(@NotNull Project project, @Nullable VirtualFile file) {
    if (file == null) {
      return;
    }

    RootUrlInfo wcRoot = SvnVcs.getInstance(project).getSvnFileUrlMapping().getWcRootForFilePath(virtualToIoFile(file));
    if (wcRoot == null) {
      return;
    }

    SvnBranchConfigurationNew configuration = SvnBranchConfigurationManager.getInstance(project).get(file);
    SvnBranchConfigurationNew clonedConfiguration = configuration.copy();

    if (new BranchConfigurationDialog(project, clonedConfiguration, wcRoot.getRepositoryUrlUrl(), file, wcRoot.getUrl()).showAndGet()) {
      SvnBranchConfigurationManager.getInstance(project).setConfiguration(file, clonedConfiguration);
    }
  }

  private static class MyListModel extends AbstractListModel<String> {
    @NotNull private final SvnBranchConfigurationNew myConfiguration;
    private List<String> myBranchUrls;

    public MyListModel(@NotNull SvnBranchConfigurationNew configuration) {
      myConfiguration = configuration;
      myBranchUrls = myConfiguration.getBranchUrls();
    }

    @NotNull
    public SvnBranchConfigurationNew getConfiguration() {
      return myConfiguration;
    }

    @Override
    public int getSize() {
      return myBranchUrls.size();
    }

    @Override
    public String getElementAt(int index) {
      return myBranchUrls.get(index);
    }

    public void fireItemAdded() {
      int index = myConfiguration.getBranchUrls().size() - 1;
      myBranchUrls = myConfiguration.getBranchUrls();
      super.fireIntervalAdded(this, index, index);
    }

    public void fireItemRemoved(int index) {
      myBranchUrls = myConfiguration.getBranchUrls();
      super.fireIntervalRemoved(this, index, index);
    }
  }
}
