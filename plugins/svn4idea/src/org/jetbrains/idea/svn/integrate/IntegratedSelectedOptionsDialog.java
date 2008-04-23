package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.idea.svn.*;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.io.File;
import java.util.*;

public class IntegratedSelectedOptionsDialog extends DialogWrapper {
  private JPanel contentPane;
  private JCheckBox myDryRunCheckbox;
  private JCheckBox myStatusBox;
  private JList myWorkingCopiesList;
  private JComponent myToolbar;
  private JLabel mySourceInfoLabel;
  private JLabel myTargetInfoLabel;

  private final Project myProject;
  private final String mySelectedBranchUrl;
  private final SvnVcs myVcs;
  private final String mySelectedRepositoryUUID;

  private DefaultActionGroup myGroup;

  public IntegratedSelectedOptionsDialog(final Project project, final SVNURL currentBranch, final String selectedBranchUrl) {
    super(project, true);
    myProject = project;
    mySelectedBranchUrl = selectedBranchUrl;
    myVcs = SvnVcs.getInstance(myProject);

    mySelectedRepositoryUUID = SvnUtil.getRepositoryUUID(myVcs, currentBranch);

    setTitle(SvnBundle.message("action.Subversion.integrate.changes.dialog.title"));
    init();

    myWorkingCopiesList.setModel(new DefaultListModel());
    myWorkingCopiesList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        setOKActionEnabled(myWorkingCopiesList.getSelectedIndex() != -1);
      }
    });
    setOKActionEnabled(myWorkingCopiesList.getSelectedIndex() != -1);

    final List<WorkingCopyInfo> workingCopyInfoList = new ArrayList<WorkingCopyInfo>();
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

    myDryRunCheckbox.setSelected(SvnConfiguration.getInstance(myVcs.getProject()).MERGE_DRY_RUN);
    myStatusBox.setSelected(SvnConfiguration.getInstance(myVcs.getProject()).UPDATE_RUN_STATUS);

    mySourceInfoLabel.setText(SvnBundle.message("action.Subversion.integrate.changes.branch.info.source.label.text", currentBranch));
    myTargetInfoLabel.setText(SvnBundle.message("action.Subversion.integrate.changes.branch.info.target.label.text", selectedBranchUrl));

    final String addText = SvnBundle.message("action.Subversion.integrate.changes.dialog.add.wc.text");
    final AnAction addAction = new AnAction(addText, addText, Icons.ADD_ICON) {
      {
        registerCustomShortcutSet(CommonShortcuts.INSERT, myWorkingCopiesList);
      }

      public void actionPerformed(final AnActionEvent e) {
        final VirtualFile[] files = FileChooser.chooseFiles(myProject, new FileChooserDescriptor(false, true, false, false, false, false));
        if (files.length > 0) {
          final File file = new File(files[0].getPath());
          if (hasDuplicate(file)) {
            return; // silently do not add duplicate
          }

          final String repositoryUUID = SvnUtil.getRepositoryUUID(myVcs, file);

          // local not consistent copy can not prevent us from integration: only remote local copy is really involved
          if ((mySelectedRepositoryUUID != null) && (! mySelectedRepositoryUUID.equals(repositoryUUID))) {
            if (OK_EXIT_CODE == Messages.showOkCancelDialog((repositoryUUID == null) ? SvnBundle.message("action.Subversion.integrate.changes.message.not.under.control.text")
                                                            : SvnBundle.message("action.Subversion.integrate.changes.message.another.wc.text"),
                                                            SvnBundle.message("action.Subversion.integrate.changes.messages.title"), UIUtil.getWarningIcon())) {
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
    myGroup.add(new AnAction(removeText, removeText, Icons.DELETE_ICON) {
      {
        registerCustomShortcutSet(CommonShortcuts.DELETE, myWorkingCopiesList);
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
    ((DefaultListModel)myWorkingCopiesList.getModel()).addElement(new WorkingCopyInfo(file.getAbsolutePath(), underProject(file)));
    SvnBranchMapperManager.getInstance().put(mySelectedBranchUrl, file.getAbsolutePath());
  }

  private boolean underProject(final File file) {
    return ExcludedFileIndex.getInstance(myProject).isInContent(SvnUtil.getVirtualFile(file.getAbsolutePath()));
  }

  public WorkingCopyInfo getSelectedWc() {
    return (WorkingCopyInfo)myWorkingCopiesList.getSelectedValue();
  }

  public void saveOptions() {
    SvnConfiguration.getInstance(myVcs.getProject()).MERGE_DRY_RUN = myDryRunCheckbox.isSelected();
    SvnConfiguration.getInstance(myVcs.getProject()).UPDATE_RUN_STATUS = myStatusBox.isSelected();
  }

  public boolean isDryRun() {
    return myDryRunCheckbox.isSelected();
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


}
