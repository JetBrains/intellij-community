// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.SelectLocationDialog;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ObjectUtils.notNull;
import static java.lang.Math.min;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.SvnUtil.isAncestor;

public class BranchConfigurationDialog extends DialogWrapper {
  private JPanel myTopPanel;
  private TextFieldWithBrowseButton myTrunkLocationTextField;
  private final JBList<String> myBranchLocationsList;
  @NotNull private final MyListModel myBranchLocationsModel;
  private JPanel myListPanel;
  @NotNull private final NewRootBunch mySvnBranchConfigManager;
  @NotNull private final VirtualFile myRoot;
  @NotNull private final Url myRootUrl;
  @NotNull private final SvnBranchConfigurationNew myConfiguration;
  private Url myTrunkUrl;

  public BranchConfigurationDialog(@NotNull Project project,
                                   @NotNull SvnBranchConfigurationNew configuration,
                                   @NotNull Url rootUrl,
                                   @NotNull VirtualFile root,
                                   @NotNull Url url) {
    super(project, true);
    myRoot = root;
    myRootUrl = rootUrl;
    myConfiguration = configuration;
    init();
    setTitle(message("configure.branches.title"));

    if (isEmptyOrSpaces(configuration.getTrunkUrl())) {
      configuration.setTrunkUrl(url.toString());
    }

    mySvnBranchConfigManager = SvnBranchConfigurationManager.getInstance(project).getSvnBranchConfigManager();

    myTrunkLocationTextField.setText(configuration.getTrunkUrl());
    myTrunkLocationTextField.addActionListener(e -> {
      Pair<Url, Url> selectionData = SelectLocationDialog.selectLocationAndRoot(project, rootUrl);

      if (selectionData != null && selectionData.first != null) {
        myTrunkLocationTextField.setText(selectionData.first.toString());
      }
    });

    myBranchLocationsModel = new MyListModel(configuration);
    myBranchLocationsList = new JBList<>(myBranchLocationsModel);

    myListPanel.add(wrapLocationsWithToolbar(project, rootUrl), BorderLayout.CENTER);
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    try {
      myTrunkUrl = createUrl(myTrunkLocationTextField.getText());
    }
    catch (SvnBindException e) {
      return new ValidationInfo(e.getMessage(), myTrunkLocationTextField.getTextField());
    }

    if (!isAncestor(myRootUrl, myTrunkUrl) || myTrunkUrl.equals(myRootUrl)) {
      return new ValidationInfo(message("configure.branches.error.wrong.url", myRootUrl), myTrunkLocationTextField.getTextField());
    }

    return null;
  }

  @Override
  protected void doOKAction() {
    if (myTrunkUrl != null) {
      myConfiguration.setTrunkUrl(myTrunkUrl.toDecodedString());
    }
    super.doOKAction();
  }

  @NotNull
  private JPanel wrapLocationsWithToolbar(@NotNull Project project, @NotNull Url rootUrl) {
    return ToolbarDecorator.createDecorator(myBranchLocationsList)
      .setAddAction(new AnActionButtonRunnable() {

        @Nullable private Url usedRootUrl;

        @Override
        public void run(AnActionButton button) {
          Pair<Url, Url> result = SelectLocationDialog.selectLocationAndRoot(project, notNull(usedRootUrl, rootUrl));
          if (result != null) {
            Url selectedUrl = result.first;
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

    if (new BranchConfigurationDialog(project, clonedConfiguration, wcRoot.getRepositoryUrl(), file, wcRoot.getUrl()).showAndGet()) {
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
