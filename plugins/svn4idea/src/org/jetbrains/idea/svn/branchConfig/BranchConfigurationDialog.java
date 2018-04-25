// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
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

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ObjectUtils.notNull;
import static java.lang.Math.min;
import static java.util.Comparator.comparing;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.SvnUtil.isAncestor;

public class BranchConfigurationDialog extends DialogWrapper {
  public static final ListCellRenderer<Url> DECODED_URL_RENDERER = new ListCellRendererWrapper<Url>() {
    @Override
    public void customize(JList list, Url value, int index, boolean selected, boolean hasFocus) {
      if (value != null) {
        setText(value.toDecodedString());
      }
    }
  };

  private JPanel myTopPanel;
  private TextFieldWithBrowseButton myTrunkLocationTextField;
  private final JBList<Url> myBranchLocationsList;
  @NotNull private final SortedListModel<Url> myBranchLocationsModel = new SortedListModel<>(comparing(Url::toDecodedString));
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

    if (configuration.getTrunk() == null) {
      configuration.setTrunk(url);
    }

    mySvnBranchConfigManager = SvnBranchConfigurationManager.getInstance(project).getSvnBranchConfigManager();

    myTrunkLocationTextField.setText(configuration.getTrunk().toDecodedString());
    myTrunkLocationTextField.addActionListener(e -> {
      Pair<Url, Url> selectionData = SelectLocationDialog.selectLocationAndRoot(project, rootUrl);

      if (selectionData != null && selectionData.first != null) {
        myTrunkLocationTextField.setText(selectionData.first.toDecodedString());
      }
    });

    myBranchLocationsModel.addAll(myConfiguration.getBranchLocations());
    myBranchLocationsList = new JBList<>(myBranchLocationsModel);
    myBranchLocationsList.setCellRenderer(DECODED_URL_RENDERER);

    myListPanel.add(wrapLocationsWithToolbar(project, rootUrl), BorderLayout.CENTER);
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    try {
      myTrunkUrl = createUrl(myTrunkLocationTextField.getText(), false);
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
      myConfiguration.setTrunk(myTrunkUrl);
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
            if (selectedUrl != null && !myConfiguration.getBranchLocations().contains(selectedUrl)) {
              myConfiguration.addBranches(selectedUrl, new InfoStorage<>(new ArrayList<>(), InfoReliability.empty));
              mySvnBranchConfigManager.reloadBranchesAsync(myRoot, selectedUrl, InfoReliability.setByUser);
              myBranchLocationsModel.add(selectedUrl);
              myBranchLocationsList.setSelectedIndex(myBranchLocationsModel.getSize() - 1);
            }
          }
        }
      })
      .setRemoveAction(button -> {
        int selectedIndex = myBranchLocationsList.getSelectedIndex();
        for (Url url : myBranchLocationsList.getSelectedValuesList()) {
          myBranchLocationsModel.remove(url);
          myConfiguration.removeBranch(url);
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
}
