package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.ui.table.TableView;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopyFormat;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class SvnMapDialog extends DialogWrapper {
  private JPanel myContentPane;
  private TableView<WCInfo> myTableView;
  private JButton myChangeFormatButton;
  private ListTableModel<WCInfo> myTableModel;

  private final Project myProject;

  public SvnMapDialog(final Project project) {
    super(project, true);
    myProject = project;

    setTitle(SvnBundle.message("dialog.show.svn.map.title"));
    init();
    
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    myTableModel.setItems(vcs.getAllWcInfos());

    myTableView.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        final Collection<WCInfo> selected = myTableView.getSelection();
        myChangeFormatButton.setEnabled((selected.size() == 1) &&
                                        (! ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()));
      }
    });
    
    myChangeFormatButton.addActionListener(new ActionListener() {
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
    });

    myChangeFormatButton.setEnabled((myTableView.getSelection().size() == 1) &&
                                    (! ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()));
  }

  public static final Topic<Runnable> WC_CONVERTED = new Topic<Runnable>("WC_CONVERTED", Runnable.class);

  protected JComponent createCenterPanel() {
    return myContentPane;
  }

  private void createUIComponents() {
    myTableModel = new ListTableModel<WCInfo>(new ColumnInfo[]{WC_ROOT_PATH, WC_URL, WC_COPY_ROOT, WC_FORMAT}, Collections.<WCInfo>emptyList(), 0);
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
    @Override
    public String getName() {
      return super.getName();
    }

    public String valueOf(final WCInfo info) {
      final WorkingCopyFormat format = info.getFormat();
      return SvnUtil.formatRepresentation(format);
    }
  };

}
