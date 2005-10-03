package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.HistoryAsTreeProvider;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ListPopup;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.TreeTablePopup;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.util.TreeItem;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Iterator;
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
      e1.printStackTrace();
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
        AbstractShowDiffAction.showDiff(diffProvider, revision.getRevisionNumber(),
                                        file, project);
      }
    };

    Window window = null;

    Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(project);
    if (focusedComponent != null) {
      if (focusedComponent instanceof Window) {
        window = (Window)focusedComponent;
      }
      else {
        window = SwingUtilities.getWindowAncestor(focusedComponent);
      }
    }
    if (window == null) {
      window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    }

    Rectangle r;
    if (window != null) {
      r = window.getBounds();
    }
    else {
      r = WindowManagerEx.getInstanceEx().getScreenBounds();
    }
    TreeUtil.expandAll(treeTable.getTree());


    TreeTablePopup popup = new TreeTablePopup(VcsBundle.message("lookup.title.vcs.file.revisions"), createMainPanel(treeTable),treeTable, runnable, project);

    popup.getWindow().pack();
    Dimension popupSize = popup.getSize();
    int x = r.x + r.width / 2 - popupSize.width / 2;
    int y = r.y + r.height / 2 - popupSize.height / 2;

    popup.show(x, y);
    
  }

  private VcsFileRevision getRevisionAt(final TreeTableView treeTable, final int index) {
    return ((TreeNodeAdapter)treeTable.getItems().get(index)).getRevision();
  }

  private JPanel createMainPanel(final TreeTableView treeTable) {
    JScrollPane scrollPane = new JScrollPane(treeTable);
    treeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    
    treeTable.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    if (treeTable.getRowCount() >= 20) {
      scrollPane.getViewport().setPreferredSize(new Dimension(treeTable.getPreferredScrollableViewportSize().width, 300));
    }
    else {
      scrollPane.getViewport().setPreferredSize(treeTable.getPreferredSize());
    }
    
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(scrollPane, BorderLayout.CENTER);
    final JTextArea textArea = createTextArea();
    treeTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final int index = treeTable.getSelectionModel().getMinSelectionIndex();
        if (index == -1) {
          textArea.setText("");
        } else {
          final VcsFileRevision revision = getRevisionAt(treeTable, index);
          textArea.setText(revision.getCommitMessage());
        }
      }
    });
    final JScrollPane textScrollPane = new JScrollPane(textArea);
    panel.add(textScrollPane, BorderLayout.SOUTH);
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
    for (Iterator<VcsFileRevision> iterator = revisions.iterator(); iterator.hasNext();) {
      model.addElement(iterator.next());
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

    Window window = null;

    Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(project);
    if (focusedComponent != null) {
      if (focusedComponent instanceof Window) {
        window = (Window)focusedComponent;
      }
      else {
        window = SwingUtilities.getWindowAncestor(focusedComponent);
      }
    }
    if (window == null) {
      window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    }

    Rectangle r;
    if (window != null) {
      r = window.getBounds();
    }
    else {
      r = WindowManagerEx.getInstanceEx().getScreenBounds();
    }

    ListPopup popup = new ListPopup(VcsBundle.message("lookup.title.vcs.file.revisions"), createListMainPanel(list),list, runnable, project);

    if (model.getSize() > 0) {
      Dimension listPreferredSize = list.getPreferredSize();
      list.setVisibleRowCount(0);
      Dimension viewPreferredSize = new Dimension(listPreferredSize.width, Math.min(listPreferredSize.height, r.height - 20));
      final Container parent = list.getParent();
      if (parent instanceof JComponent) {
        ((JComponent)parent).setPreferredSize(viewPreferredSize);
      }
    }

    popup.getWindow().pack();
    Dimension popupSize = popup.getSize();
    int x = r.x + r.width / 2 - popupSize.width / 2;
    int y = r.y + r.height / 2 - popupSize.height / 2;

    popup.show(x, y);
  }

  private JPanel createListMainPanel(final JList list) {
    final JPanel jPanel = new JPanel(new BorderLayout());
    jPanel.add(ListPopup.createScrollPane(list), BorderLayout.CENTER);
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
      for (Iterator<TreeItem<VcsFileRevision>> iterator = children.iterator(); iterator.hasNext();) {
        TreeItem<VcsFileRevision> treeItem = iterator.next();
        new TreeNodeAdapter(this, treeItem, treeItem.getChildren());
      }
    }

    public VcsFileRevision getRevision() {
      return myRevision.getData();
    }
  }
}
