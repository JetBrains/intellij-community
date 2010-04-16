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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.ui.table.TableView;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.idea.svn.*;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class SvnMapDialog extends DialogWrapper {
  private JPanel myContentPane;
  private TableView<WCInfo> myTableView;
  private JButton myChangeFormatButton;        
  private JButton myCorrectButton;
  private JLabel myWarningLabel;
  private JButton myRefresh;
  private ListTableModel<WCInfo> myTableModel;

  private final Project myProject;
  private final ActionListener myChangeFormatListener;
  private MessageBusConnection myMessageBusConnection;

  public SvnMapDialog(final Project project) {
    super(project, true);
    myProject = project;

    setTitle(SvnBundle.message("dialog.show.svn.map.title"));
    init();
    
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final List<WCInfo> infoList = vcs.getAllWcInfos();
    myTableModel.setItems(infoList);

    final boolean promptForCorrection = vcs.getSvnFileUrlMapping().rootsDiffer();
    if (promptForCorrection) {
      myWarningLabel.setText(SvnBundle.message("action.working.copies.map.correct.warning.text"));
      myWarningLabel.setUI(new MultiLineLabelUI());
      myCorrectButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          correctMappings(vcs, infoList);
        }
      });
    }
    myCorrectButton.setVisible(promptForCorrection);
    myWarningLabel.setVisible(promptForCorrection);

    myTableView.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        final Collection<WCInfo> selected = myTableView.getSelection();
        myChangeFormatButton.setEnabled((selected.size() == 1) &&
                                        (! ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()));
      }
    });

    myChangeFormatListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final Collection<WCInfo> selected = myTableView.getSelection();
        if (selected.size() != 1) {
          return;
        }
        final WCInfo wcInfo = selected.iterator().next();
        File path = new File(wcInfo.getPath());
        if (! wcInfo.isIsWcRoot()) {
          path = SvnUtil.getWorkingCopyRoot(path);
        }

        ChangeFormatDialog dialog = new ChangeFormatDialog(project, path, false, ! wcInfo.isIsWcRoot());
        dialog.setData(true, wcInfo.getFormat().getOption());
        dialog.show();
        if (! dialog.isOK()) {
          return;
        }
        final String newMode = dialog.getUpgradeMode();
        if (! wcInfo.getFormat().getOption().equals(newMode)) {
          final WorkingCopyFormat newFormat = WorkingCopyFormat.getInstance(newMode);
          final Task.Backgroundable task = new SvnFormatWorker(project, newFormat, wcInfo);
          doOKAction();
          ProgressManager.getInstance().run(task);
        }
      }
    };
    myChangeFormatButton.addActionListener(myChangeFormatListener);

    myChangeFormatButton.setEnabled((myTableView.getSelection().size() == 1) &&
                                    (! ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()));

    myRefresh.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final SvnVcs vcs = SvnVcs.getInstance(myProject);
        subscribeToUpdates(vcs);
        vcs.invokeRefreshSvnRoots(false);
        myRefresh.setEnabled(false);
        /*final List<WCInfo> infoList = vcs.getAllWcInfos();
        myTableModel.setItems(infoList);
        myTableView.repaint();*/
      }
    });
  }

  @Override
  protected String getHelpId() {
    return "reference.vcs.svn.working.copies.information";
  }

  private void subscribeToUpdates(final SvnVcs vcs) {
    if (myMessageBusConnection == null) {
      myMessageBusConnection = myProject.getMessageBus().connect(getDisposable());
      final ModalityState state = ModalityState.current();
      myMessageBusConnection.subscribe(SvnVcs.ROOTS_RELOADED, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              final List<WCInfo> infoList = vcs.getAllWcInfos();
              myTableModel.setItems(infoList);
              myRefresh.setEnabled(true);
              myTableView.repaint();
            }
          }, state);
        }
      });
    }
  }

  private void correctMappings(final SvnVcs vcs, final List<WCInfo> infos) {
    final ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(vcs.getProject());
    final List<VcsDirectoryMapping> mappings = manager.getDirectoryMappings();
    final List<VcsDirectoryMapping> newMappings = new ArrayList<VcsDirectoryMapping>();
    final String svnVcsName = vcs.getName();
    for (VcsDirectoryMapping mapping : mappings) {
      if (! svnVcsName.equals(mapping.getVcs())) {
        newMappings.add(mapping);
      }
    }
    for (WCInfo info : infos) {
      newMappings.add(new VcsDirectoryMapping(FileUtil.toSystemIndependentName(info.getPath()), svnVcsName));
    }
    manager.setDirectoryMappings(newMappings);

    // table did not changed
    myCorrectButton.setVisible(false);
    myWarningLabel.setVisible(false);
  }

  public static final Topic<Runnable> WC_CONVERTED = new Topic<Runnable>("WC_CONVERTED", Runnable.class);

  public JComponent createCenterPanel() {
    return myContentPane;
  }

  private void createUIComponents() {
    myTableModel = new ListTableModel<WCInfo>(new ColumnInfo[]{WC_ROOT_PATH, WC_URL, WC_COPY_ROOT, WC_FORMAT, WC_TYPE}, Collections.<WCInfo>emptyList(), 0);
    myTableView = new TableView<WCInfo>(myTableModel);
  }

  private static final ColumnInfo<WCInfo, String> WC_ROOT_PATH = new ColumnInfo<WCInfo, String>(SvnBundle.message("dialog.show.svn.map.table.header.column.wcpath.title")) {
    public String valueOf(final WCInfo info) {
      return info.getPath();
    }
  };
  private static final ColumnInfo<WCInfo, String> WC_URL = new ColumnInfo<WCInfo, String>(SvnBundle.message("dialog.show.svn.map.table.header.column.wcurl.title")) {
    public String valueOf(final WCInfo info) {
      return info.getUrl().toString();
    }
  };
  private static final ColumnInfo<WCInfo, String> WC_COPY_ROOT = new ColumnInfo<WCInfo, String>(SvnBundle.message("dialog.show.svn.map.table.header.column.wcroot.title")) {
    public String valueOf(final WCInfo info) {
      return info.isIsWcRoot() ? "yes" : "no";
    }
  };
  private static final ColumnInfo<WCInfo, String> WC_FORMAT = new ColumnInfo<WCInfo, String>(SvnBundle.message("dialog.show.svn.map.table.header.column.format.title")) {
    public String valueOf(final WCInfo info) {
      final WorkingCopyFormat format = info.getFormat();
      return SvnUtil.formatRepresentation(format);
    }
  };
  private static final ColumnInfo<WCInfo, String> WC_TYPE = new ColumnInfo<WCInfo, String>("Type") {
    public String valueOf(final WCInfo info) {
      final NestedCopyType type = info.getType();
      return type == null ? "" : type.getName();
    }
  };

}
