package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.ui.table.TableView;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.tmatesoft.svn.core.wc.SVNWCClient;

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
    // todo separate class
    myChangeFormatButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final Collection<WCInfo> selected = myTableView.getSelection();
        if (selected.size() != 1) {
          return;
        }
        final WCInfo wcInfo = selected.iterator().next();
        final File path = new File(wcInfo.getPath());

        ChangeFormatDialog dialog = new ChangeFormatDialog(project, path, false);
        dialog.setData(true, wcInfo.getFormat().getOption());
        dialog.show();
        if (! dialog.isOK()) {
          return;
        }
        final String newMode = dialog.getUpgradeMode();
        if (! wcInfo.getFormat().getOption().equals(newMode)) {
          final WorkingCopyFormat newFormat = WorkingCopyFormat.getInstance(newMode);
          final Task.Backgroundable task = new Task.Backgroundable(project, SvnBundle.message("action.change.wcopy.format.task.title"), false,
                                                                   PerformInBackgroundOption.DEAF) {
            private Throwable myException;

            @Override
            public void onCancel() {
              onSuccess();
            }

            @Override
            public void onSuccess() {
              ProjectLevelVcsManager.getInstance(project).stopBackgroundVcsOperation();

              if (myException != null) {
                AbstractVcsHelper.getInstance(myProject)
                    .showErrors(Collections.singletonList(new VcsException(myException)), SvnBundle.message("action.change.wcopy.format.task.title"));
              } else {
                SvnConfiguration configuration = SvnConfiguration.getInstance(project);
                String upgradeMode = configuration.getUpgradeMode();
                final WorkingCopyFormat configurationFormat = WorkingCopyFormat.getInstance(upgradeMode);

                if (newFormat.getFormat() < configurationFormat.getFormat()) {
                  final int result = Messages.showYesNoCancelDialog(SvnBundle.message("action.change.wcopy.format.after.change.settings",
                                                                                      formatRepresentation(newFormat),
                                                                                      formatRepresentation(wcInfo.getFormat())),
                                                                    SvnBundle.message("action.change.wcopy.format.task.title"),
                                                                    Messages.getWarningIcon());
                  if (result == OK_EXIT_CODE) {
                    configuration.setUpgradeMode(newFormat.getOption());
                  }
                }
              }
            }

            public void run(@NotNull final ProgressIndicator indicator) {
              ProjectLevelVcsManager.getInstance(project).startBackgroundVcsOperation();

              indicator.setIndeterminate(true);
              indicator.setText(SvnBundle.message("action.change.wcopy.format.task.progress.text", path.getAbsolutePath(),
                                                  formatRepresentation(wcInfo.getFormat()), formatRepresentation(newFormat)));

              final SvnVcs vcs = SvnVcs.getInstance(project);
              final SVNWCClient wcClient = vcs.createWCClient();
              try {
                wcClient.doSetWCFormat(path, newFormat.getFormat());
              } catch (Throwable e) {
                myException = e;
              }

              ApplicationManager.getApplication().getMessageBus().syncPublisher(WC_CONVERTED).run();
            }
          };
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
    myTableModel = new ListTableModel<WCInfo>(new ColumnInfo[]{WC_ROOT_PATH, WC_URL, WC_FORMAT}, Collections.<WCInfo>emptyList(), 0);
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
  private static final ColumnInfo<WCInfo, String> WC_FORMAT = new ColumnInfo<WCInfo, String>(SvnBundle.message("dialog.show.svn.map.table.header.column.format.title")) {
    @Override
    public String getName() {
      return super.getName();
    }

    public String valueOf(final WCInfo info) {
      final WorkingCopyFormat format = info.getFormat();
      return formatRepresentation(format);
    }
  };

  private static String formatRepresentation(final WorkingCopyFormat format) {
    if (WorkingCopyFormat.ONE_DOT_FIVE.equals(format)) {
      // todo put into somewhere
      return SvnBundle.message("dialog.show.svn.map.table.version15.text");
    } else if (WorkingCopyFormat.ONE_DOT_FOUR.equals(format)) {
      return SvnBundle.message("dialog.show.svn.map.table.version14.text");
    } else if (WorkingCopyFormat.ONE_DOT_THREE.equals(format)) {
      return SvnBundle.message("dialog.show.svn.map.table.version13.text");
    }
    return "";
  }

}
