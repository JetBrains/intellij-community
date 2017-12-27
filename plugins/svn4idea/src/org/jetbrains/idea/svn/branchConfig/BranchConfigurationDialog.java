// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.SelectLocationDialog;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ObjectUtils.notNull;
import static java.lang.Math.min;
import static org.jetbrains.idea.svn.SvnUtil.isAncestor;

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
                                   @NotNull Url rootUrl,
                                   @NotNull VirtualFile root,
                                   @NotNull Url url) {
    super(project, true);
    myRoot = root;
    init();
    setTitle(SvnBundle.message("configure.branches.title"));

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

  private class TrunkUrlValidator extends DocumentAdapter {
    private final Url myRootUrl;
    private final SvnBranchConfigurationNew myConfiguration;

    private TrunkUrlValidator(final Url rootUrl, final SvnBranchConfigurationNew configuration) {
      myRootUrl = rootUrl;
      myConfiguration = configuration;
    }

    protected void textChanged(final DocumentEvent e) {
      Url url = parseUrl(myTrunkLocationTextField.getText());

      if (url != null) {
        boolean areNotSame = isAncestor(myRootUrl, url) && !url.equals(myRootUrl);

        if (areNotSame) {
          myConfiguration.setTrunkUrl(url.toDecodedString());
        }
        myErrorPrompt.setText(areNotSame ? "" : SvnBundle.message("configure.branches.error.wrong.url", myRootUrl));
      }
    }

    @Nullable
    private Url parseUrl(@NotNull String url) {
      Url result = null;

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
