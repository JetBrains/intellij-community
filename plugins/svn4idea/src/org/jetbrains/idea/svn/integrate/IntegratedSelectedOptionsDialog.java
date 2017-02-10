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
package org.jetbrains.idea.svn.integrate;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.branchConfig.SvnBranchMapperManager;
import org.jetbrains.idea.svn.info.Info;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.io.File;
import java.util.*;

public class IntegratedSelectedOptionsDialog extends DialogWrapper {
  private JPanel contentPane;
  private JCheckBox myDryRunCheckbox;
  private JList myWorkingCopiesList;
  private JComponent myToolbar;
  private JLabel mySourceInfoLabel;
  private JLabel myTargetInfoLabel;
  private JLabel myWcListTitleLabel;
  private JCheckBox myIgnoreWhitespacesCheckBox;

  private final Project myProject;
  private final String mySelectedBranchUrl;
  private final SvnVcs myVcs;
  private final String mySelectedRepositoryUUID;

  private DefaultActionGroup myGroup;

  private boolean myMustSelectBeforeOk;

  public IntegratedSelectedOptionsDialog(final Project project, final SVNURL currentBranch, final String selectedBranchUrl) {
    super(project, true);
    myMustSelectBeforeOk = true;
    myProject = project;
    mySelectedBranchUrl = selectedBranchUrl;
    myVcs = SvnVcs.getInstance(myProject);

    mySelectedRepositoryUUID = SvnUtil.getRepositoryUUID(myVcs, currentBranch);

    setTitle(SvnBundle.message("action.Subversion.integrate.changes.dialog.title"));
    init();

    myWorkingCopiesList.setModel(new DefaultListModel());
    myWorkingCopiesList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        setOKActionEnabled((! myMustSelectBeforeOk) || (myWorkingCopiesList.getSelectedIndex() != -1));
      }
    });
    setOKActionEnabled((! myMustSelectBeforeOk) || (myWorkingCopiesList.getSelectedIndex() != -1));

    final List<WorkingCopyInfo> workingCopyInfoList = new ArrayList<>();
    final Set<String> workingCopies = SvnBranchMapperManager.getInstance().get(mySelectedBranchUrl);
    if (workingCopies != null) {
      for (String workingCopy : workingCopies) {
        workingCopyInfoList.add(new WorkingCopyInfo(workingCopy, underProject(new File(workingCopy))));
      }
    }
    Collections.sort(workingCopyInfoList, WorkingCopyInfoComparator.getInstance());

    for (WorkingCopyInfo info : workingCopyInfoList) {
      ((DefaultListModel)myWorkingCopiesList.getModel()).addElement(info);
    }
    if (!workingCopyInfoList.isEmpty()) {
      myWorkingCopiesList.setSelectedIndex(0);
    }

    SvnConfiguration svnConfig = SvnConfiguration.getInstance(myVcs.getProject());
    myDryRunCheckbox.setSelected(svnConfig.isMergeDryRun());
    myIgnoreWhitespacesCheckBox.setSelected(svnConfig.isIgnoreSpacesInMerge());

    mySourceInfoLabel.setText(SvnBundle.message("action.Subversion.integrate.changes.branch.info.source.label.text", currentBranch));
    myTargetInfoLabel.setText(SvnBundle.message("action.Subversion.integrate.changes.branch.info.target.label.text", selectedBranchUrl));

    final String addText = SvnBundle.message("action.Subversion.integrate.changes.dialog.add.wc.text");
    final AnAction addAction = new AnAction(addText, addText, IconUtil.getAddIcon()) {
      {
        registerCustomShortcutSet(CommonShortcuts.INSERT, myWorkingCopiesList);
      }

      public void actionPerformed(final AnActionEvent e) {
        final VirtualFile vFile = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), myProject, null);
        if (vFile != null) {
          final File file = new File(vFile.getPath());
          if (hasDuplicate(file)) {
            return; // silently do not add duplicate
          }

          final String repositoryUUID = SvnUtil.getRepositoryUUID(myVcs, file);

          // local not consistent copy can not prevent us from integration: only remote local copy is really involved
          if ((mySelectedRepositoryUUID != null) && (! mySelectedRepositoryUUID.equals(repositoryUUID))) {
            if (Messages.OK == Messages.showOkCancelDialog((repositoryUUID == null) ? SvnBundle.message("action.Subversion.integrate.changes.message.not.under.control.text")
                                                            : SvnBundle.message("action.Subversion.integrate.changes.message.another.wc.text"),
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

    final String removeText = SvnBundle.message("action.Subversion.integrate.changes.dialog.remove.wc.text");
    myGroup.add(new AnAction(removeText, removeText, PlatformIcons.DELETE_ICON) {
      {
        registerCustomShortcutSet(CommonShortcuts.getDelete(), myWorkingCopiesList);
      }

      public void update(final AnActionEvent e) {
        final Presentation presentation = e.getPresentation();
        final int idx = (myWorkingCopiesList == null) ? -1 : myWorkingCopiesList.getSelectedIndex();
        presentation.setEnabled(idx != -1);
      }

      public void actionPerformed(final AnActionEvent e) {
        final int idx = myWorkingCopiesList.getSelectedIndex();
        if (idx != -1) {
          final DefaultListModel model = (DefaultListModel)myWorkingCopiesList.getModel();
          final WorkingCopyInfo info = (WorkingCopyInfo)model.get(idx);
          model.removeElementAt(idx);
          SvnBranchMapperManager.getInstance().remove(mySelectedBranchUrl, new File(info.getLocalPath()));
        }
      }
    });
  }

  public void setSelectedWcPath(final String path) {
    final ListModel model = myWorkingCopiesList.getModel();
    final int size = model.getSize();
    for (int i = 0; i < size; i++) {
      final WorkingCopyInfo info = (WorkingCopyInfo) model.getElementAt(i);
      if (info.getLocalPath().equals(path)) {
        myWorkingCopiesList.setSelectedValue(info, true);
        return;
      }
    }
  }

  public void selectWcopyRootOnly() {
    myMustSelectBeforeOk = false;
    setTitle(SvnBundle.message("dialog.Subversion.select.working.copy.title"));
    myIgnoreWhitespacesCheckBox.setVisible(false);
    myDryRunCheckbox.setVisible(false);
    myWcListTitleLabel.setText(SvnBundle.message("dialog.Subversion.select.working.copy.wcopy.list.title"));
  }

  private void createUIComponents() {
    myGroup = new DefaultActionGroup();
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, myGroup, false);
    myToolbar = actionToolbar.getComponent();
  }

  private boolean hasDuplicate(final File file) {
    final DefaultListModel model = (DefaultListModel)myWorkingCopiesList.getModel();
    final String path = file.getAbsolutePath();

    for (int i = 0; i < model.getSize(); i++) {
      final WorkingCopyInfo info = (WorkingCopyInfo)model.getElementAt(i);
      if (path.equals(info.getLocalPath())) {
        return true;
      }
    }
    return false;
  }

  private void onOkToAdd(final File file) {
    final WorkingCopyInfo info = new WorkingCopyInfo(file.getAbsolutePath(), underProject(file));
    final DefaultListModel model = (DefaultListModel) myWorkingCopiesList.getModel();
    model.addElement(info);
    myWorkingCopiesList.setSelectedValue(info, true);
    SvnBranchMapperManager.getInstance().put(mySelectedBranchUrl, file.getAbsolutePath());
  }

  private boolean underProject(final File file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        final VirtualFile vf = SvnUtil.getVirtualFile(file.getAbsolutePath());
        return (vf == null) || PeriodicalTasksCloser.getInstance().safeGetService(myProject, FileIndexFacade.class).isInContent(vf);
      }
    });
  }

  public WorkingCopyInfo getSelectedWc() {
    return (WorkingCopyInfo)myWorkingCopiesList.getSelectedValue();
  }

  public void saveOptions() {
    SvnConfiguration svnConfig = SvnConfiguration.getInstance(myVcs.getProject());
    svnConfig.setMergeDryRun(myDryRunCheckbox.isSelected());
    svnConfig.setIgnoreSpacesInMerge(myIgnoreWhitespacesCheckBox.isSelected());
  }

  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public static class WorkingCopyInfoComparator implements Comparator<WorkingCopyInfo> {
    private static final WorkingCopyInfoComparator instance = new WorkingCopyInfoComparator();

    public static WorkingCopyInfoComparator getInstance() {
      return instance;
    }

    private WorkingCopyInfoComparator() {
    }

    public int compare(final WorkingCopyInfo o1, final WorkingCopyInfo o2) {
      return o1.getLocalPath().compareTo(o2.getLocalPath());
    }
  }

  @Nullable
  private static SVNURL realTargetUrl(final SvnVcs vcs, final WorkingCopyInfo info, final String targetBranchUrl) {
    final Info svnInfo = vcs.getInfo(info.getLocalPath());
    final SVNURL svnurl = svnInfo != null ? svnInfo.getURL() : null;

    return (svnurl != null) && (svnurl.toString().startsWith(targetBranchUrl)) ? svnurl : null;
  }

  @Nullable
  public static Pair<WorkingCopyInfo, SVNURL> selectWorkingCopy(final Project project, final SVNURL currentBranch, final String targetBranch,
                                                                final boolean showIntegrationParameters,
                                                                final String selectedLocalBranchPath, final String dialogTitle) {
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
      ApplicationManager.getApplication().saveAll();
      dialog.saveOptions();

      final WorkingCopyInfo info = dialog.getSelectedWc();
      if (info != null) {
        final File file = new File(info.getLocalPath());
        if ((!file.exists()) || (!file.isDirectory())) {
          Messages.showErrorDialog(SvnBundle.message("action.Subversion.integrate.changes.error.target.not.dir.text"),
                                   SvnBundle.message("action.Subversion.integrate.changes.messages.title"));
          return null;
        }

        final SVNURL targetUrl = realTargetUrl(SvnVcs.getInstance(project), info, targetBranch);

        if (targetUrl == null) {
          Messages.showErrorDialog(SvnBundle.message("action.Subversion.integrate.changes.error.not.versioned.text"),
                                   SvnBundle.message("action.Subversion.integrate.changes.messages.title"));
          return null;
        }
        return Pair.create(info, targetUrl);
      }
    }
    return null;
  }
}
