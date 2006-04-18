package com.intellij.openapi.vcs.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.HistoryAsTreeProvider;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.util.TreeItem;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.List;

public class CompareWithSelectedRevisionAction extends AbstractVcsAction {

  private static final ColumnInfo<TreeNodeAdapter,String> BRANCH_COLUMN = new ColumnInfo<TreeNodeAdapter, String>(VcsBundle.message("column.name.revisions.list.branch")){
    public String valueOf(final TreeNodeAdapter object) {
      return object.getRevision().getBranchName();
    }
  };

  private static final ColumnInfo<TreeNodeAdapter,String> REVISION_COLUMN = new ColumnInfo<TreeNodeAdapter, String>(VcsBundle.message("column.name.revision.list.revision")){
    public String valueOf(final TreeNodeAdapter object) {
      return object.getRevision().getRevisionNumber().asString();
    }
  };

  private static final ColumnInfo<TreeNodeAdapter,String> DATE_COLUMN = new ColumnInfo<TreeNodeAdapter, String>(VcsBundle.message("column.name.revisions.list.filter")){
    public String valueOf(final TreeNodeAdapter object) {
      return VcsRevisionListCellRenderer.DATE_FORMAT.format(object.getRevision().getRevisionDate());
    }
  };

  private static final ColumnInfo<TreeNodeAdapter,String> AUTHOR_COLUMN = new ColumnInfo<TreeNodeAdapter, String>(VcsBundle.message("column.name.revision.list.author")){
    public String valueOf(final TreeNodeAdapter object) {
      return object.getRevision().getAuthor();
    }
  };

  public void update(VcsContext e, Presentation presentation) {
    AbstractShowDiffAction.updateDiffAction(presentation, e);
  }


  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }

  protected void actionPerformed(VcsContext vcsContext) {
    final VirtualFile file = vcsContext.getSelectedFiles()[0];
    final Project project = vcsContext.getProject();
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    final VcsHistoryProvider vcsHistoryProvider = vcs.getVcsHistoryProvider();

    try {
      final VcsHistorySession session = vcsHistoryProvider.createSessionFor(new FilePathImpl(file));
      final List<VcsFileRevision> revisions = session.getRevisionList();
      final HistoryAsTreeProvider treeHistoryProvider = vcsHistoryProvider.getTreeHistoryProvider();
      if (treeHistoryProvider != null) {
        showTreePopup(treeHistoryProvider.createTreeOn(revisions), file, project, vcs.getDiffProvider());
      }
      else {
        showListPopup(revisions, vcs, file, project);
      }

    }
    catch (VcsException e1) {
      Messages.showErrorDialog(VcsBundle.message("message.text.cannot.show.differences"), CommonBundle.message("title.error"));
    }


  }

  private void showTreePopup(final List<TreeItem<VcsFileRevision>> roots, final VirtualFile file, final Project project, final DiffProvider diffProvider) {
    final TreeTableView treeTable = new TreeTableView(new ListTreeTableModelOnColumns(new TreeNodeAdapter(null, null, roots),
                                                                                      new ColumnInfo[]{BRANCH_COLUMN, REVISION_COLUMN,
                                                                                      DATE_COLUMN, AUTHOR_COLUMN}));
    Runnable runnable = new Runnable() {
      public void run() {
        int index = treeTable.getSelectionModel().getMinSelectionIndex();
        if (index == -1) {
          return;
        }
        VcsFileRevision revision = getRevisionAt(treeTable, index);
        if (revision != null) {
          AbstractShowDiffAction.showDiff(diffProvider, revision.getRevisionNumber(),
                                          file, project);
        }
      }
    };

    treeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    new PopupChooserBuilder(treeTable).
      setTitle(VcsBundle.message("lookup.title.vcs.file.revisions")).
      setItemChoosenCallback(runnable).
      setSouthComponent(createCommentsPanel(treeTable)).
      createPopup().
      showCenteredInCurrentWindow(project);
  }


  @Nullable private VcsFileRevision getRevisionAt(final TreeTableView treeTable, final int index) {
    final List items = treeTable.getItems();
    if (items.size() <= index) {
      return null;
    } else {
      return ((TreeNodeAdapter)items.get(index)).getRevision();
    }

  }

  private JPanel createCommentsPanel(final TreeTableView treeTable) {
    JPanel panel = new JPanel(new BorderLayout());
    final JTextArea textArea = createTextArea();
    treeTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final int index = treeTable.getSelectionModel().getMinSelectionIndex();
        if (index == -1) {
          textArea.setText("");
        } else {
          final VcsFileRevision revision = getRevisionAt(treeTable, index);
          if (revision != null) {
            textArea.setText(revision.getCommitMessage());
          } else {
            textArea.setText("");
          }
        }
      }
    });
    final JScrollPane textScrollPane = new JScrollPane(textArea);
    panel.add(textScrollPane, BorderLayout.CENTER);
    textScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.lightGray),VcsBundle.message("border.selected.revision.commit.message")));
    return panel;
  }

  private JTextArea createTextArea() {
    final JTextArea textArea = new JTextArea();
    textArea.setRows(5);
    textArea.setEditable(false);
    textArea.setWrapStyleWord(true);
    textArea.setLineWrap(true);
    return textArea;
  }

  private void showListPopup(final List<VcsFileRevision> revisions, final AbstractVcs vcs, final VirtualFile file, final Project project) {
    final DefaultListModel model = new DefaultListModel();
    for (final VcsFileRevision revision : revisions) {
      model.addElement(revision);
    }
    final JList list = new JList(model);
    list.setCellRenderer(new VcsRevisionListCellRenderer());
    Runnable runnable = new Runnable() {
      public void run() {
        int index = list.getSelectedIndex();
        if (index == -1 || index >= list.getModel().getSize()) {
          return;
        }
        VcsFileRevision revision = (VcsFileRevision)list.getSelectedValue();
        AbstractShowDiffAction.showDiff(vcs.getDiffProvider(), revision.getRevisionNumber(),
                                        file, project);
      }
    };

    if (list.getModel().getSize() == 0) {
      list.clearSelection();
    }
    new ListSpeedSearch(list);

    new PopupChooserBuilder(list).
      setSouthComponent(createCommentsPanel(list)).
      setTitle(VcsBundle.message("lookup.title.vcs.file.revisions")).
      setItemChoosenCallback(runnable).
      createPopup().
      showCenteredInCurrentWindow(project);
  }

  private JPanel createCommentsPanel(final JList list) {
    final JTextArea textArea = createTextArea();
    list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final int index = list.getMinSelectionIndex();
        if (index == -1) {
          textArea.setText("");
        } else {
          final VcsFileRevision revision = (VcsFileRevision)list.getModel().getElementAt(index);
          textArea.setText(revision.getCommitMessage());
        }
      }
    });

    JPanel jPanel = new JPanel(new BorderLayout());
    final JScrollPane textScrollPane = new JScrollPane(textArea);
    textScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.lightGray),VcsBundle.message("border.selected.revision.commit.message")));
    jPanel.add(textScrollPane, BorderLayout.SOUTH);

    jPanel.setPreferredSize(new Dimension(300, jPanel.getPreferredSize().height + 10));
    return jPanel;
  }

  private static class TreeNodeAdapter extends DefaultMutableTreeNode {
    private TreeItem<VcsFileRevision> myRevision;

    public TreeNodeAdapter(TreeNodeAdapter parent, TreeItem<VcsFileRevision> revision, List<TreeItem<VcsFileRevision>> children) {
      if (parent != null) {
        parent.add(this);
      }
      myRevision = revision;
      for (TreeItem<VcsFileRevision> treeItem : children) {
        new TreeNodeAdapter(this, treeItem, treeItem.getChildren());
      }
    }

    public VcsFileRevision getRevision() {
      return myRevision.getData();
    }
  }
}
