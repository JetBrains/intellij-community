package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class ChangesTreeList extends JPanel {
  private Tree myTree;
  private JList myList;
  private Project myProject;
  private final boolean myShowCheckboxes;
  private boolean myShowFlatten;

  private Collection<Change> myIncludedChanges;
  private Runnable myDoubleClickHandler = EmptyRunnable.getInstance();

  @NonNls private static final String TREE_CARD = "Tree";
  @NonNls private static final String LIST_CARD = "List";
  @NonNls private static final String ROOT = "root";
  private CardLayout myCards;

  public ChangesTreeList(final Project project, Collection<Change> initiallyIncluded, final boolean showCheckboxes) {
    myProject = project;
    myShowCheckboxes = showCheckboxes;
    myIncludedChanges = new HashSet<Change>(initiallyIncluded);

    myCards = new CardLayout();

    setLayout(myCards);

    final int checkboxWidth = new JCheckBox().getPreferredSize().width;
    myTree = new Tree(new ChangesBrowserNode(ROOT)) {
      public Dimension getPreferredScrollableViewportSize() {
        Dimension size = super.getPreferredScrollableViewportSize();
        size = new Dimension(size.width + 10, size.height);
        return size;
      }

      protected void processMouseEvent(MouseEvent e) {
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
          int row = myTree.getRowForLocation(e.getX(), e.getY());
          if (row >= 0) {
            final Rectangle baseRect = myTree.getRowBounds(row);
            baseRect.setSize(checkboxWidth, baseRect.height);
            if (baseRect.contains(e.getPoint())) {
              myTree.setSelectionRow(row);
              toggleSelection();
            }
          }
        }
        super.processMouseEvent(e);
      }

      public int getToggleClickCount() {
        return -1;
      }
    };

    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);

    myTree.setCellRenderer(new MyTreeCellRenderer());

    myList = new JList(new DefaultListModel());
    myList.setVisibleRowCount(10);

    add(new JScrollPane(myList), LIST_CARD);
    add(new JScrollPane(myTree), TREE_CARD);

    new ListSpeedSearch(myList) {
      protected String getElementText(Object element) {
        if (element instanceof Change) {
          return ChangesUtil.getFilePath((Change)element).getName();
        }
        return super.getElementText(element);
      }
    };

    myList.setCellRenderer(new MyListCellRenderer());

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        toggleSelection();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        includeSelection();
      }

    }, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        excludeSelection();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        final int idx = myList.locationToIndex(e.getPoint());
        if (idx >= 0) {
          final Rectangle baseRect = myList.getCellBounds(idx, idx);
          baseRect.setSize(checkboxWidth, baseRect.height);
          if (baseRect.contains(e.getPoint())) {
            toggleSelection();
            e.consume();
          }
          else if (e.getClickCount() == 2) {
            myDoubleClickHandler.run();
            e.consume();
          }
        }
      }
    });

    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        final int row = myTree.getRowForLocation(e.getPoint().x, e.getPoint().y);
        if (row >= 0) {
          final Rectangle baseRect = myTree.getRowBounds(row);
          baseRect.setSize(checkboxWidth, baseRect.height);
          if (!baseRect.contains(e.getPoint()) && e.getClickCount() == 2) {
            myDoubleClickHandler.run();
            e.consume();
          }
        }
      }
    });
  }

  public void setDoubleClickHandler(final Runnable doubleClickHandler) {
    myDoubleClickHandler = doubleClickHandler;
  }

  public void installPopupHandler(ActionGroup group) {
    PopupHandler.installUnknownPopupHandler(myList, group, ActionManager.getInstance());
    PopupHandler.installUnknownPopupHandler(myTree, group, ActionManager.getInstance());
  }

  public Dimension getPreferredSize() {
    return new Dimension(400, 400);
  }

  public boolean isShowFlatten() {
    return myShowFlatten;
  }

  public void setShowFlatten(final boolean showFlatten) {
    myShowFlatten = showFlatten;
    myCards.show(this, myShowFlatten ? LIST_CARD : TREE_CARD);
    if (myList.hasFocus() || myTree.hasFocus()) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          requestFocus();
        }
      });
    }
  }


  public void requestFocus() {
    if (myShowFlatten) {
      myList.requestFocus();
    }
    else {
      myTree.requestFocus();
    }
  }

  public void setChangesToDisplay(final List<Change> changes) {
    final DefaultListModel listModel = (DefaultListModel)myList.getModel();
    final List<Change> sortedChanges = new ArrayList<Change>(changes);
    Collections.sort(sortedChanges, new Comparator<Change>() {
      public int compare(final Change o1, final Change o2) {
        return ChangesUtil.getFilePath(o1).getName().compareToIgnoreCase(ChangesUtil.getFilePath(o2).getName());
      }
    });

    listModel.removeAllElements();
    for (Change change : sortedChanges) {
      listModel.addElement(change);
    }

    TreeModelBuilder builder = new TreeModelBuilder(myProject, false);
    final DefaultTreeModel model = builder.buildModel(changes);
    myTree.setModel(model);

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        TreeUtil.expandAll(myTree);

        if (myIncludedChanges.size() > 0) {
          int listSelection = 0;
          int count = 0;
          for (Change change : changes) {
            if (myIncludedChanges.contains(change)) {
              listSelection = count;
              break;
            }
            count ++;
          }

          ChangesBrowserNode root = (ChangesBrowserNode)model.getRoot();
          Enumeration enumeration = root.depthFirstEnumeration();

          while (enumeration.hasMoreElements()) {
            ChangesBrowserNode node = (ChangesBrowserNode)enumeration.nextElement();
            final NodeState state = getNodeStatus(node);
            if (node != root && state == NodeState.CLEAR) {
              myTree.collapsePath(new TreePath(node.getPath()));
            }
          }

          enumeration = root.depthFirstEnumeration();
          int scrollRow = 0;
          while (enumeration.hasMoreElements()) {
            ChangesBrowserNode node = (ChangesBrowserNode)enumeration.nextElement();
            final NodeState state = getNodeStatus(node);
            if (state == NodeState.FULL && node.isLeaf()) {
              scrollRow = myTree.getRowForPath(new TreePath(node.getPath()));
              break;
            }
          }

          if (changes.size() > 0) {
            myList.setSelectedIndex(listSelection);
            myList.ensureIndexIsVisible(listSelection);

            myTree.setSelectionRow(scrollRow);
            TreeUtil.showRowCentered(myTree, scrollRow, false);
          }
        }
      }
    });
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  private void toggleSelection() {
    boolean hasExcluded = false;
    for (Change value : getSelectedChanges()) {
      if (!myIncludedChanges.contains(value)) {
        hasExcluded = true;
      }
    }

    if (hasExcluded) {
      includeSelection();
    }
    else {
      excludeSelection();
    }

    repaint();
  }

  private void includeSelection() {
    for (Change change : getSelectedChanges()) {
      myIncludedChanges.add(change);
    }
    repaint();
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  private void excludeSelection() {
    for (Change change : getSelectedChanges()) {
      myIncludedChanges.remove(change);
    }
    repaint();
  }

  @NotNull
  public Change[] getSelectedChanges() {
    if (myShowFlatten) {
      final Object[] o = myList.getSelectedValues();
      final Change[] changes = new Change[o.length];
      for (int i = 0; i < changes.length; i++) {
        changes[i] = (Change)o[i];
      }

      return changes;
    }
    else {
      List<Change> changes = new ArrayList<Change>();
      final TreePath[] paths = myTree.getSelectionPaths();
      if (paths != null) {
        for (TreePath path : paths) {
          ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
          changes.addAll(node.getAllChangesUnder());
        }
      }

      return changes.toArray(new Change[changes.size()]);
    }
  }

  public Change getLeadSelection() {
    if (myShowFlatten) {
      final int index = myList.getLeadSelectionIndex();
      if (index < 0) return null;
      return (Change)myList.getModel().getElementAt(index);
    }
    else {
      final TreePath path = myTree.getSelectionPath();
      if (path == null) return null;
      final List<Change> changes = ((ChangesBrowserNode)path.getLastPathComponent()).getAllChangesUnder();
      return changes.size() > 0 ? changes.get(0) : null;
    }
  }

  public void includeChange(final Change change) {
    myIncludedChanges.add(change);
  }

  public void excludeChange(final Change change) {
    myIncludedChanges.remove(change);
  }

  public boolean isIncluded(final Change change) {
    return myIncludedChanges.contains(change);
  }

  public Collection<Change> getIncludedChanges() {
    return myIncludedChanges;
  }

  public AnAction[] getTreeActions() {
    final AnAction[] actions = new AnAction[]{
      new ExpandAllAction(myTree) {
        public void update(AnActionEvent e) {
          e.getPresentation().setVisible(!myShowFlatten);
        }
      },
      new CollapseAllAction(myTree) {
        public void update(AnActionEvent e) {
          e.getPresentation().setVisible(!myShowFlatten);
        }
      }
    };
    actions [0].registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_EXPAND_ALL)),
      myTree);
    actions [1].registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_COLLAPSE_ALL)),
      myTree);
    return actions;
  }

  private class MyTreeCellRenderer extends JPanel implements TreeCellRenderer {
    private final ChangeBrowserNodeRenderer myTextRenderer;
    private final JCheckBox myCheckBox;


    public MyTreeCellRenderer() {
      super(new BorderLayout());
      myCheckBox = new JCheckBox();
      myTextRenderer = new ChangeBrowserNodeRenderer(myProject, false);

      myCheckBox.setBackground(null);
      setBackground(null);

      if (myShowCheckboxes) {
        add(myCheckBox, BorderLayout.WEST);
      }

      add(myTextRenderer, BorderLayout.CENTER);
    }

    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      myTextRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      if (myShowCheckboxes) {
        ChangesBrowserNode node = (ChangesBrowserNode)value;

        NodeState state = getNodeStatus(node);
        myCheckBox.setSelected(state != NodeState.CLEAR);
        myCheckBox.setEnabled(state != NodeState.PARTIAL);
        revalidate();

        return this;
      }
      else {
        return myTextRenderer;
      }
    }
  }

  private static enum NodeState {
    FULL, CLEAR, PARTIAL
  }

  private NodeState getNodeStatus(ChangesBrowserNode node) {
    boolean hasIncluded = false;
    boolean hasExcluded = false;

    for (Change change : node.getAllChangesUnder()) {
      if (myIncludedChanges.contains(change)) {
        hasIncluded = true;
      }
      else {
        hasExcluded = true;
      }
    }

    if (hasIncluded && hasExcluded) return NodeState.PARTIAL;
    if (hasIncluded) return NodeState.FULL;
    return NodeState.CLEAR;
  }

  private class MyListCellRenderer extends JPanel implements ListCellRenderer {
    private final ColoredListCellRenderer myTextRenderer;
    public final JCheckBox myCheckbox;

    public MyListCellRenderer() {
      super(new BorderLayout());
      myCheckbox = new JCheckBox();
      myTextRenderer = new ColoredListCellRenderer() {
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          Change change = (Change)value;
          final FilePath path = ChangesUtil.getFilePath(change);
          setIcon(path.getFileType().getIcon());
          append(path.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, getColor(change), null));
          final File parentFile = path.getIOFile().getParentFile();
          if (parentFile != null) {
            append(" (" + parentFile.getPath() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }

        private Color getColor(final Change change) {
          return change.getFileStatus().getColor();
        }
      };

      myCheckbox.setBackground(null);
      setBackground(null);

      if (myShowCheckboxes) {
        add(myCheckbox, BorderLayout.WEST);
      }
      add(myTextRenderer, BorderLayout.CENTER);
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      myTextRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (myShowCheckboxes) {
        myCheckbox.setSelected(myIncludedChanges.contains(value));
        return this;
      }
      else {
        return myTextRenderer;
      }
    }
  }
}
