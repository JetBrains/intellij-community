package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.util.Icons;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
public abstract class ChangesTreeList<T> extends JPanel {
  private Tree myTree;
  private JList myList;
  private Project myProject;
  private final boolean myShowCheckboxes;
  private final boolean myHighlightProblems;
  private boolean myShowFlatten;

  private Collection<T> myIncludedChanges;
  private Runnable myDoubleClickHandler = EmptyRunnable.getInstance();

  @NonNls private static final String TREE_CARD = "Tree";
  @NonNls private static final String LIST_CARD = "List";
  @NonNls private static final String ROOT = "root";
  private CardLayout myCards;

  @NonNls private final static String FLATTEN_OPTION_KEY = "ChangesBrowser.SHOW_FLATTEN";

  public ChangesTreeList(final Project project, Collection<T> initiallyIncluded, final boolean showCheckboxes,
                         final boolean highlightProblems) {
    myProject = project;
    myShowCheckboxes = showCheckboxes;
    myHighlightProblems = highlightProblems;
    myIncludedChanges = new HashSet<T>(initiallyIncluded);

    myCards = new CardLayout();

    setLayout(myCards);

    final int checkboxWidth = new JCheckBox().getPreferredSize().width;
    myTree = new Tree(ChangesBrowserNode.create(myProject, ROOT)) {
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

    new MyToggleSelectionAction().registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), this);

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

    setShowFlatten(PropertiesComponent.getInstance(myProject).isTrueValue(FLATTEN_OPTION_KEY));
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

  public void setChangesToDisplay(final List<T> changes) {
    final DefaultListModel listModel = (DefaultListModel)myList.getModel();
    final List<T> sortedChanges = new ArrayList<T>(changes);
    Collections.sort(sortedChanges, new Comparator<T>() {
      public int compare(final T o1, final T o2) {
        return TreeModelBuilder.getPathForObject(o1).getName().compareToIgnoreCase(TreeModelBuilder.getPathForObject(o1).getName());
      }
    });

    listModel.removeAllElements();
    for (T change : sortedChanges) {
      listModel.addElement(change);
    }

    final DefaultTreeModel model = buildTreeModel(changes);
    myTree.setModel(model);

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        TreeUtil.expandAll(myTree);

        if (myIncludedChanges.size() > 0) {
          int listSelection = 0;
          int count = 0;
          for (T change : changes) {
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

  protected abstract DefaultTreeModel buildTreeModel(final List<T> changes);

  @SuppressWarnings({"SuspiciousMethodCalls"})
  private void toggleSelection() {
    boolean hasExcluded = false;
    for (T value : getSelectedChanges()) {
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
    for (T change : getSelectedChanges()) {
      myIncludedChanges.add(change);
    }
    repaint();
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  private void excludeSelection() {
    for (T change : getSelectedChanges()) {
      myIncludedChanges.remove(change);
    }
    repaint();
  }

  @NotNull
  public List<T> getSelectedChanges() {
    if (myShowFlatten) {
      final Object[] o = myList.getSelectedValues();
      final List<T> changes = new ArrayList<T>();
      for (Object anO : o) {
        changes.add((T)anO);
      }

      return changes;
    }
    else {
      List<T> changes = new ArrayList<T>();
      final TreePath[] paths = myTree.getSelectionPaths();
      if (paths != null) {
        for (TreePath path : paths) {
          ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
          changes.addAll(getSelectedObjects(node));
        }
      }

      return changes;
    }
  }

  protected abstract List<T> getSelectedObjects(final ChangesBrowserNode node);

  @Nullable
  public T getLeadSelection() {
    if (myShowFlatten) {
      final int index = myList.getLeadSelectionIndex();
      ListModel listModel = myList.getModel();
      if (index < 0 || index >= listModel.getSize()) return null;
      //noinspection unchecked
      return (T)listModel.getElementAt(index);
    }
    else {
      final TreePath path = myTree.getSelectionPath();
      if (path == null) return null;
      final List<T> changes = getSelectedObjects(((ChangesBrowserNode)path.getLastPathComponent()));
      return changes.size() > 0 ? changes.get(0) : null;
    }
  }

  public void includeChange(final T change) {
    myIncludedChanges.add(change);
    myTree.repaint();
    myList.repaint();
  }

  public void excludeChange(final T change) {
    myIncludedChanges.remove(change);
    myTree.repaint();
    myList.repaint();
  }

  public boolean isIncluded(final T change) {
    return myIncludedChanges.contains(change);
  }

  public Collection<T> getIncludedChanges() {
    return myIncludedChanges;
  }

  public AnAction[] getTreeActions() {
    final ToggleShowDirectoriesAction directoriesAction = new ToggleShowDirectoriesAction();
    final ExpandAllAction expandAllAction = new ExpandAllAction(myTree) {
      public void update(AnActionEvent e) {
        e.getPresentation().setVisible(!myShowFlatten);
      }
    };
    final CollapseAllAction collapseAllAction = new CollapseAllAction(myTree) {
      public void update(AnActionEvent e) {
        e.getPresentation().setVisible(!myShowFlatten);
      }
    };
    final SelectAllAction selectAllAction = new SelectAllAction();
    final AnAction[] actions = new AnAction[]{directoriesAction, expandAllAction, collapseAllAction, selectAllAction};
    directoriesAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK)),
      this);
    expandAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_EXPAND_ALL)),
      myTree);
    collapseAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_COLLAPSE_ALL)),
      myTree);
    selectAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_A, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK)),
      this);
    return actions;
  }

  private class MyTreeCellRenderer extends JPanel implements TreeCellRenderer {
    private final ChangesBrowserNodeRenderer myTextRenderer;
    private final JCheckBox myCheckBox;


    public MyTreeCellRenderer() {
      super(new BorderLayout());
      myCheckBox = new JCheckBox();
      myTextRenderer = new ChangesBrowserNodeRenderer(myProject, false, myHighlightProblems);

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

    for (T change : getSelectedObjects(node)) {
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
          final FilePath path = TreeModelBuilder.getPathForObject(value);
          setIcon(path.getFileType().getIcon());
          final FileStatus fileStatus;
          if (value instanceof Change) {
            fileStatus = ((Change) value).getFileStatus();
          }
          else {
            final VirtualFile virtualFile = path.getVirtualFile();
            if (virtualFile != null) {
              fileStatus = FileStatusManager.getInstance(myProject).getStatus(virtualFile);
            }
            else {
              fileStatus = FileStatus.NOT_CHANGED;
            }
          }
          append(path.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fileStatus.getColor(), null));
          final File parentFile = path.getIOFile().getParentFile();
          if (parentFile != null) {
            append(" (" + parentFile.getPath() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
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

  private class MyToggleSelectionAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
      toggleSelection();
    }
  }

  public class ToggleShowDirectoriesAction extends ToggleAction {
    public ToggleShowDirectoriesAction() {
      super(VcsBundle.message("changes.action.show.directories.text"),
            VcsBundle.message("changes.action.show.directories.description"),
            Icons.DIRECTORY_CLOSED_ICON);
    }

    public boolean isSelected(AnActionEvent e) {
      return !PropertiesComponent.getInstance(myProject).isTrueValue(FLATTEN_OPTION_KEY);
    }

    public void setSelected(AnActionEvent e, boolean state) {
      PropertiesComponent.getInstance(myProject).setValue(FLATTEN_OPTION_KEY, String.valueOf(!state));
      setShowFlatten(!state);
    }
  }

  private class SelectAllAction extends AnAction {
    private SelectAllAction() {
      super("Select All", "Select all items", IconLoader.getIcon("/actions/selectall.png"));
    }

    public void actionPerformed(final AnActionEvent e) {
      if (myShowFlatten) {
        final int count = myList.getModel().getSize();
        if (count > 0) {
          myList.setSelectionInterval(0, count-1);
        }
      }
      else {
        final int count = myTree.getRowCount() - 1;
        if (count > 0) {
          myTree.setSelectionInterval(0, count-1);
        }
      }
    }
  }

}
