// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.branchConfig.SvnBranchMapperManager;
import org.jetbrains.idea.svn.info.Info;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.isAncestor;

public class IntegratedSelectedOptionsDialog extends DialogWrapper {
  private final DefaultListModel<WorkingCopyInfo> myWorkingCopyInfoModel;
  private JPanel contentPane;
  private JCheckBox myDryRunCheckbox;
  private JList<WorkingCopyInfo> myWorkingCopiesList;
  private JComponent myToolbar;
  private JLabel mySourceInfoLabel;
  private JLabel myTargetInfoLabel;
  private JLabel myWcListTitleLabel;
  private JCheckBox myIgnoreWhitespacesCheckBox;

  private final Project myProject;
  @NotNull private final Url mySelectedBranchUrl;
  private final SvnVcs myVcs;
  private final String mySelectedRepositoryUUID;

  private DefaultActionGroup myGroup;

  private boolean myMustSelectBeforeOk;

  public IntegratedSelectedOptionsDialog(final Project project, final Url currentBranch, @NotNull Url selectedBranchUrl) {
    super(project, true);
    myMustSelectBeforeOk = true;
    myProject = project;
    mySelectedBranchUrl = selectedBranchUrl;
    myVcs = SvnVcs.getInstance(myProject);

    mySelectedRepositoryUUID = SvnUtil.getRepositoryUUID(myVcs, currentBranch);

    setTitle(message("dialog.title.integrate.to.branch"));
    init();

    myWorkingCopyInfoModel = new DefaultListModel<>();
    myWorkingCopiesList.setModel(myWorkingCopyInfoModel);
    myWorkingCopiesList
      .addListSelectionListener(e -> setOKActionEnabled((!myMustSelectBeforeOk) || (myWorkingCopiesList.getSelectedIndex() != -1)));
    setOKActionEnabled((! myMustSelectBeforeOk) || (myWorkingCopiesList.getSelectedIndex() != -1));

    final List<WorkingCopyInfo> workingCopyInfoList = new ArrayList<>();
    final Set<String> workingCopies = SvnBranchMapperManager.getInstance().get(mySelectedBranchUrl);
    if (workingCopies != null) {
      for (String workingCopy : workingCopies) {
        workingCopyInfoList.add(new WorkingCopyInfo(workingCopy, underProject(new File(workingCopy))));
      }
    }
    workingCopyInfoList.sort(WorkingCopyInfoComparator.getInstance());

    for (WorkingCopyInfo info : workingCopyInfoList) {
      myWorkingCopyInfoModel.addElement(info);
    }
    if (!workingCopyInfoList.isEmpty()) {
      myWorkingCopiesList.setSelectedIndex(0);
    }

    SvnConfiguration svnConfig = myVcs.getSvnConfiguration();
    myDryRunCheckbox.setSelected(svnConfig.isMergeDryRun());
    myIgnoreWhitespacesCheckBox.setSelected(svnConfig.isIgnoreSpacesInMerge());

    mySourceInfoLabel.setText(message("action.Subversion.integrate.changes.branch.info.source.label.text", currentBranch));
    myTargetInfoLabel
      .setText(message("action.Subversion.integrate.changes.branch.info.target.label.text", selectedBranchUrl.toDecodedString()));

    final String addText = message("action.Subversion.integrate.changes.dialog.add.wc.text");
    final AnAction addAction = new DumbAwareAction(addText, addText, IconUtil.getAddIcon()) {
      {
        registerCustomShortcutSet(CommonShortcuts.INSERT, myWorkingCopiesList);
      }

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        final VirtualFile vFile = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), myProject, null);
        if (vFile != null) {
          final File file = virtualToIoFile(vFile);
          if (hasDuplicate(file)) {
            return; // silently do not add duplicate
          }

          final String repositoryUUID = SvnUtil.getRepositoryUUID(myVcs, file);

          // local not consistent copy can not prevent us from integration: only remote local copy is really involved
          if ((mySelectedRepositoryUUID != null) && (! mySelectedRepositoryUUID.equals(repositoryUUID))) {
            if (Messages.OK == Messages.showOkCancelDialog((repositoryUUID == null) ? message("action.Subversion.integrate.changes.message.not.under.control.text")
                                                            : message("action.Subversion.integrate.changes.message.another.wc.text"),
                                                            getTitle(), UIUtil.getWarningIcon())) {
              onOkToAdd(file);
            }
          }
          else {
            onOkToAdd(file);
          }
        }
      }
    };

    myGroup.add(addAction);

    final String removeText = message("action.Subversion.integrate.changes.dialog.remove.wc.text");
    myGroup.add(new DumbAwareAction(removeText, removeText, PlatformIcons.DELETE_ICON) {
      {
        registerCustomShortcutSet(CommonShortcuts.getDelete(), myWorkingCopiesList);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void update(@NotNull final AnActionEvent e) {
        final Presentation presentation = e.getPresentation();
        final int idx = myWorkingCopiesList.getSelectedIndex();
        presentation.setEnabled(idx != -1);
      }

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        final int idx = myWorkingCopiesList.getSelectedIndex();
        if (idx != -1) {
          final WorkingCopyInfo info = myWorkingCopyInfoModel.get(idx);
          myWorkingCopyInfoModel.removeElementAt(idx);
          SvnBranchMapperManager.getInstance().remove(mySelectedBranchUrl, new File(info.getLocalPath()));
        }
      }
    });
  }

  public void setSelectedWcPath(final String path) {
    final int size = myWorkingCopyInfoModel.getSize();
    for (int i = 0; i < size; i++) {
      final WorkingCopyInfo info = myWorkingCopyInfoModel.getElementAt(i);
      if (info.getLocalPath().equals(path)) {
        myWorkingCopiesList.setSelectedValue(info, true);
        return;
      }
    }
  }

  public void selectWcopyRootOnly() {
    myMustSelectBeforeOk = false;
    setTitle(message("dialog.Subversion.select.working.copy.title"));
    myIgnoreWhitespacesCheckBox.setVisible(false);
    myDryRunCheckbox.setVisible(false);
    myWcListTitleLabel.setText(message("dialog.Subversion.select.working.copy.wcopy.list.title"));
  }

  private void createUIComponents() {
    myGroup = new DefaultActionGroup();
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("SvnIntegratedSelectedOptionsDialog", myGroup, false);
    myToolbar = actionToolbar.getComponent();
  }

  private boolean hasDuplicate(final File file) {
    final String path = file.getAbsolutePath();

    for (int i = 0; i < myWorkingCopyInfoModel.getSize(); i++) {
      final WorkingCopyInfo info = myWorkingCopyInfoModel.getElementAt(i);
      if (path.equals(info.getLocalPath())) {
        return true;
      }
    }
    return false;
  }

  private void onOkToAdd(final File file) {
    final WorkingCopyInfo info = new WorkingCopyInfo(file.getAbsolutePath(), underProject(file));
    myWorkingCopyInfoModel.addElement(info);
    myWorkingCopiesList.setSelectedValue(info, true);
    SvnBranchMapperManager.getInstance().put(mySelectedBranchUrl, file);
  }

  private boolean underProject(final File file) {
    return ReadAction.compute(() -> {
      final VirtualFile vf = SvnUtil.getVirtualFile(file.getAbsolutePath());
      return (vf == null) || myProject.getService(FileIndexFacade.class).isInContent(vf);
    });
  }

  public WorkingCopyInfo getSelectedWc() {
    return myWorkingCopiesList.getSelectedValue();
  }

  public void saveOptions() {
    SvnConfiguration svnConfig = myVcs.getSvnConfiguration();
    svnConfig.setMergeDryRun(myDryRunCheckbox.isSelected());
    svnConfig.setIgnoreSpacesInMerge(myIgnoreWhitespacesCheckBox.isSelected());
  }

  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public static final class WorkingCopyInfoComparator implements Comparator<WorkingCopyInfo> {
    private static final WorkingCopyInfoComparator instance = new WorkingCopyInfoComparator();

    public static WorkingCopyInfoComparator getInstance() {
      return instance;
    }

    private WorkingCopyInfoComparator() {
    }

    @Override
    public int compare(final WorkingCopyInfo o1, final WorkingCopyInfo o2) {
      return o1.getLocalPath().compareTo(o2.getLocalPath());
    }
  }

  @Nullable
  private static Url realTargetUrl(@NotNull SvnVcs vcs, @NotNull WorkingCopyInfo info, @NotNull Url targetBranchUrl) {
    Info svnInfo = vcs.getInfo(info.getLocalPath());
    Url url = svnInfo != null ? svnInfo.getUrl() : null;

    return url != null && isAncestor(targetBranchUrl, url) ? url : null;
  }

  @Nullable
  public static Pair<WorkingCopyInfo, Url> selectWorkingCopy(final Project project,
                                                             final Url currentBranch,
                                                             @NotNull Url targetBranch,
                                                             final boolean showIntegrationParameters,
                                                             final String selectedLocalBranchPath,
                                                             @DialogTitle @Nullable String dialogTitle) {
    final IntegratedSelectedOptionsDialog dialog = new IntegratedSelectedOptionsDialog(project, currentBranch, targetBranch);
    if (!showIntegrationParameters) {
      dialog.selectWcopyRootOnly();
    }
    if (selectedLocalBranchPath != null) {
      dialog.setSelectedWcPath(selectedLocalBranchPath);
    }
    if (dialogTitle != null) {
      dialog.setTitle(dialogTitle);
    }
    if (dialog.showAndGet()) {
      StoreUtil.saveDocumentsAndProjectSettings(project);
      dialog.saveOptions();

      final WorkingCopyInfo info = dialog.getSelectedWc();
      if (info != null) {
        final File file = new File(info.getLocalPath());
        if ((!file.exists()) || (!file.isDirectory())) {
          Messages.showErrorDialog(message("dialog.message.integrate.changes.error.target.not.dir"),
                                   message("dialog.title.integrate.to.branch"));
          return null;
        }

        final Url targetUrl = realTargetUrl(SvnVcs.getInstance(project), info, targetBranch);

        if (targetUrl == null) {
          Messages.showErrorDialog(message("dialog.message.integrate.changes.error.not.versioned"),
                                   message("dialog.title.integrate.to.branch"));
          return null;
        }
        return Pair.create(info, targetUrl);
      }
    }
    return null;
  }
}
