package com.intellij.openapi.vcs.history.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.SortableColumnModel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class VcsHistoryDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.history.impl.VcsHistoryDialog");
  private final AbstractVcs myActiveVcs;

  private final static DateFormat DATE_FORMAT = SimpleDateFormat
    .getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT, Locale.getDefault());

  private final DiffPanel myDiffPanel;
  private final Project myProject;

  private final static ColumnInfo REVISION = new ColumnInfo(VcsBundle.message("column.name.revision.list.revision")) {
    public Object valueOf(Object object) {
      return ((VcsFileRevision)object).getRevisionNumber();
    }

  };

  private final static ColumnInfo DATE = new ColumnInfo(VcsBundle.message("column.name.revision.list.date")) {
    public Object valueOf(Object object) {
      Date date = ((VcsFileRevision)object).getRevisionDate();
      if (date == null) return "";
      return DATE_FORMAT.format(date);
    }

  };

  private final static ColumnInfo MESSAGE = new ColumnInfo(VcsBundle.message("column.name.revision.list.message")) {
    public Object valueOf(Object object) {
      return ((VcsFileRevision)object).getCommitMessage();
    }

  };

  private final static ColumnInfo AUTHOR = new ColumnInfo(VcsBundle.message("column.name.revision.list.author")) {
    public Object valueOf(Object object) {
      return ((VcsFileRevision)object).getAuthor();
    }

  };

  private final static ColumnInfo[] COLUMNS = new ColumnInfo[]{REVISION, DATE, AUTHOR, MESSAGE};

  private final TableView myList;
  protected final List<VcsFileRevision> myRevisions;
  private Splitter mySplitter;
  private final VirtualFile myFile;
  private final JCheckBox myChangesOnlyCheckBox = new JCheckBox(VcsBundle.message("checkbox.show.changed.revisions.only"));
  private final Map<VcsFileRevision, String> myCachedContents = new com.intellij.util.containers.HashMap<VcsFileRevision, String>();
  private final JTextArea myComments = new JTextArea();
  private static final int CURRENT = 0;
  private boolean myIsInLoading = false;
  private final String myHelpId;
  private boolean myIsDisposed = false;
  private final FileType myContentFileType;


  public VcsHistoryDialog(Project project,
                          final VirtualFile file,
                          final VcsHistoryProvider vcsHistoryProvider,
                          VcsHistorySession session,
                          AbstractVcs vcs){
    super(project, true);
    myProject = project;
    myActiveVcs = vcs;
    myRevisions = new ArrayList<VcsFileRevision>();
    myFile = file;
    myHelpId = vcsHistoryProvider.getHelpId();
    myList = new TableView(new ListTableModel(createColumns(vcsHistoryProvider.getRevisionColumns())));
    ((SortableColumnModel)myList.getModel()).setSortable(false);

    myDiffPanel = DiffManager.getInstance().createDiffPanel(getWindow(), myProject);

    myRevisions.addAll(session.getRevisionList());
    final VcsRevisionNumber currentRevisionNumber = session.getCurrentRevisionNumber();
    if (currentRevisionNumber != null) {
      myRevisions.add(new CurrentRevision(file, currentRevisionNumber));
    }
    Collections.sort(myRevisions, new Comparator<VcsFileRevision>() {
      public int compare(VcsFileRevision rev1, VcsFileRevision rev2){
        return VcsHistoryUtil.compare(rev1, rev2);
      }
    });
    Collections.reverse(myRevisions);

    myContentFileType = FileTypeManager.getInstance().getFileTypeByFile(file);

    updateRevisionsList();

    mySplitter = new Splitter(true, getVcsConfiguration().FILE_HISTORY_DIALOG_SPLITTER_PROPORTION);

    mySplitter.setFirstComponent(myDiffPanel.getComponent());
    mySplitter.setSecondComponent(createBottomPanel());

    mySplitter.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        if (Splitter.PROP_PROPORTION.equals(evt.getPropertyName())) {
          getVcsConfiguration().FILE_HISTORY_DIALOG_SPLITTER_PROPORTION
          = ((Float)evt.getNewValue()).floatValue();
        }
      }
    });


    myList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (myList.getSelectedRowCount() == 1) {
          myComments.setText(myRevisions.get(myList.getSelectedRow()).getCommitMessage());
          myComments.setCaretPosition(0);
        }
        else {
          myComments.setText("");
        }
        updateDiff();
      }
    });

    myChangesOnlyCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateRevisionsList();
      }
    });

    init();

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        myList.getSelectionModel().addSelectionInterval(0, 0);
      }
    });

    setTitle(VcsBundle.message("dialog.title.history.for.file", file.getName()));
  }

  public void show() {
    myList.getSelectionModel().setSelectionInterval(0, 0);
    super.show();
  }

  private static ColumnInfo[] createColumns(ColumnInfo[] additionalColumns) {
    if (additionalColumns == null) {
      return COLUMNS;
    }

    ColumnInfo[] result = new ColumnInfo[additionalColumns.length + COLUMNS.length];

    System.arraycopy(COLUMNS, 0, result, 0, COLUMNS.length);
    System.arraycopy(additionalColumns, 0, result, COLUMNS.length, additionalColumns.length);

    return result;
  }

  protected synchronized String getContentOf(VcsFileRevision revision) {
    LOG.assertTrue(myCachedContents.containsKey(revision), revision.getRevisionNumber().asString());
    return myCachedContents.get(revision);
  }

  private synchronized void loadContentsFor(final VcsFileRevision[] revisions) {
    if (myIsInLoading) return;
    myIsInLoading = true;
    synchronized (myCachedContents) {

      final VcsFileRevision[] revisionsToLoad = revisionsNeededToBeLoaded(revisions);
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          progressIndicator.pushState();
          try {
            for (int i = 0; i < revisionsToLoad.length; i++) {
              final VcsFileRevision vcsFileRevision = revisionsToLoad[i];
              progressIndicator.setText2(VcsBundle.message("progress.text2.loading.revision", vcsFileRevision.getRevisionNumber()));
              progressIndicator.setFraction((double)i / (double)revisionsToLoad.length);
              if (!myCachedContents.containsKey(vcsFileRevision)) {
                try {
                  vcsFileRevision.loadContent();
                }
                catch (final VcsException e) {
                  ApplicationManager.getApplication().invokeLater(new Runnable() {
                    public void run() {
                      Messages.showErrorDialog(VcsBundle.message("message.text.cannot.load.version.bocause.of.error",
                                                                 vcsFileRevision.getRevisionNumber(), e.getLocalizedMessage()), VcsBundle.message("message.title.load.version"));
                    }
                  });
                } catch (ProcessCanceledException ex){
                  return;
                }
                String content = null;
                try {
                  content = new String(vcsFileRevision.getContent(),
                                       myFile.getCharset().name());
                }
                catch (IOException e) {
                  LOG.error(e);
                }
                myCachedContents.put(vcsFileRevision, content);

              }
            }
          }
          finally {
            myIsInLoading = false;
            progressIndicator.popState();
          }

        }
      }, VcsBundle.message("progress.title.loading.contents"), false, myProject);
    }

  }

  protected VcsFileRevision[] revisionsNeededToBeLoaded(VcsFileRevision[] revisions) {
    return revisions;
  }

  private void updateRevisionsList() {
    if (myIsInLoading) return;
    if (myChangesOnlyCheckBox.isSelected()) {
      loadContentsFor(myRevisions.toArray(new VcsFileRevision[myRevisions.size()]));
      ((ListTableModel)myList.getModel()).setItems(filteredRevisions());
      ((ListTableModel)myList.getModel()).fireTableDataChanged();
      updateDiff(0, 0);

    }
    else {
      ((ListTableModel)myList.getModel()).setItems(myRevisions);
      ((ListTableModel)myList.getModel()).fireTableDataChanged();
    }

  }

  private List<VcsFileRevision> filteredRevisions() {
    ArrayList<VcsFileRevision> result = new ArrayList<VcsFileRevision>();
    VcsFileRevision nextRevision = myRevisions.get(myRevisions.size() - 1);
    result.add(nextRevision);
    for (int i = myRevisions.size() - 2; i >= 0; i--) {
      VcsFileRevision vcsFileRevision = myRevisions.get(i);
      if (getContentToShow(nextRevision).equals(getContentToShow(vcsFileRevision))) continue;
      result.add(vcsFileRevision);
      nextRevision = vcsFileRevision;
    }
    Collections.reverse(result);
    return result;
  }

  private synchronized void updateDiff() {
    int[] selectedIndices = myList.getSelectedRows();
    if (selectedIndices.length == 0) {
      updateDiff(CURRENT, CURRENT);
    }
    else if (selectedIndices.length == 1) {
      updateDiff(selectedIndices[0], CURRENT);
    }
    else {
      updateDiff(selectedIndices[selectedIndices.length - 1], selectedIndices[0]);
    }
  }

  private synchronized void updateDiff(int first, int second) {
    if (myIsDisposed) return;
    List items = ((ListTableModel)myList.getModel()).getItems();
    VcsFileRevision firstRev = (VcsFileRevision)items.get(first);
    VcsFileRevision secondRev = (VcsFileRevision)items.get(second);

    if (VcsHistoryUtil.compare(firstRev, secondRev) > 0) {
      VcsFileRevision tmp = firstRev;
      firstRev = secondRev;
      secondRev = tmp;
    }

    loadContentsFor(new VcsFileRevision[]{firstRev, secondRev});
    myDiffPanel.setContents(new SimpleContent(getContentToShow(firstRev), myContentFileType),
                            new SimpleContent(getContentToShow(secondRev), myContentFileType));
    myDiffPanel.setTitle1(VcsBundle.message("diff.content.title.revision.number", firstRev.getRevisionNumber()));
    myDiffPanel.setTitle2(VcsBundle.message("diff.content.title.revision.number", secondRev.getRevisionNumber()));

  }

  public synchronized void dispose() {
    myIsDisposed = true;
    myDiffPanel.dispose();
    super.dispose();
  }

  protected String getContentToShow(final VcsFileRevision firstRev) {
    return getContentOf(firstRev);
  }

  private JComponent createBottomPanel() {
    Splitter splitter = new Splitter(true, getVcsConfiguration()
                                           .FILE_HISTORY_DIALOG_COMMENTS_SPLITTER_PROPORTION);

    splitter.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        if (Splitter.PROP_PROPORTION.equals(evt.getPropertyName())) {
          getVcsConfiguration().FILE_HISTORY_DIALOG_COMMENTS_SPLITTER_PROPORTION
          = ((Float)evt.getNewValue()).floatValue();
        }
      }
    });

    JPanel tablePanel = new JPanel(new BorderLayout());
    tablePanel.add(createTablePanel(), BorderLayout.CENTER);
    tablePanel.add(myChangesOnlyCheckBox, BorderLayout.NORTH);

    splitter.setFirstComponent(tablePanel);
    splitter.setSecondComponent(createComments());

    return splitter;
  }

  private VcsConfiguration getVcsConfiguration() {
    return myActiveVcs.getConfiguration();
  }

  private JComponent createComments() {
    myComments.setRows(5);
    myComments.setEditable(false);
    myComments.setLineWrap(true);
    return new JScrollPane(myComments);
  }

  private JComponent createTablePanel() {
    return ScrollPaneFactory.createScrollPane(myList);
  }

  protected JComponent createCenterPanel() {
    return mySplitter;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpId);
  }

  protected Action[] createActions() {
    Action okAction = getOKAction();
    okAction.putValue(Action.NAME, ExecutionBundle.message("close.tab.action.name"));
    if (myHelpId != null) {
      return new Action[]{okAction, getHelpAction()};
    }
    else {
      return new Action[]{okAction};
    }
  }

  protected String getDimensionServiceKey() {
    return "VCS.FileHistoryDialog";
  }

}
