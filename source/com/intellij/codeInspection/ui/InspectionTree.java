/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 4, 2001
 * Time: 5:19:35 PM
 */
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;

public class InspectionTree extends Tree {
  private final HashSet<Object> myExpandedUserObjects;
  private SelectionPath mySelectionPath;

  public InspectionTree(final Project project) {
    super(new InspectionRootNode(project));

    setCellRenderer(new CellRenderer());//project));
    setShowsRootHandles(true);
    putClientProperty("JTree.lineStyle", "Angled");
    addTreeWillExpandListener(new ExpandListener());

    myExpandedUserObjects = new HashSet<Object>();
    myExpandedUserObjects.add(project);

    TreeToolTipHandler.install(this);
    TreeUtil.installActions(this);
    new TreeSpeedSearch(this);

    addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        TreePath newSelection = e.getNewLeadSelectionPath();
        if (newSelection != null) {
          mySelectionPath = new SelectionPath(newSelection);
        }
      }
    });
  }

  public void removeAllNodes() {
    getRoot().removeAllChildren();
    nodeStructureChanged(getRoot());
  }

  public InspectionTreeNode getRoot() {
    return (InspectionTreeNode)getModel().getRoot();
  }

  public void nodeStructureChanged(InspectionTreeNode node) {
    ((DefaultTreeModel)getModel()).nodeStructureChanged(node);
  }

  private class ExpandListener implements TreeWillExpandListener {
    public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
      final InspectionTreeNode node = (InspectionTreeNode)event.getPath().getLastPathComponent();
      myExpandedUserObjects.add(node.getUserObject());
      if (node instanceof RefElementNode && !node.children().hasMoreElements()) {
        ((RefElementNode)node).loadChildren();
        sortChildren(node);
      }

      // Smart expand
      if (node.getChildCount() == 1) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            expandPath(new TreePath(node.getPath()));
          }
        });
      }
    }

    public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
      InspectionTreeNode node = (InspectionTreeNode)event.getPath().getLastPathComponent();
      myExpandedUserObjects.remove(node.getUserObject());
    }
  }

  public void restoreExpantionAndSelection() {
    restoreExpantion();
    if (mySelectionPath != null) {
      mySelectionPath.restore();
    }
  }

  private void restoreExpantion() {
    restoreExpantionStatus((InspectionTreeNode)getModel().getRoot());
  }


  private void restoreExpantionStatus(InspectionTreeNode node) {
    if (myExpandedUserObjects.contains(node.getUserObject())) {
      TreeNode[] pathToNode = node.getPath();
      expandPath(new TreePath(pathToNode));
      Enumeration children = node.children();
      while (children.hasMoreElements()) {
        InspectionTreeNode childNode = (InspectionTreeNode)children.nextElement();
        restoreExpantionStatus(childNode);
      }
    }
  }

  private class CellRenderer extends ColoredTreeCellRenderer {
    /*  private Project myProject;
      InspectionManagerEx myManager;
      public CellRenderer(Project project) {
        myProject = project;
        myManager = (InspectionManagerEx)InspectionManager.getInstance(myProject);
      }*/

    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      InspectionTreeNode node = (InspectionTreeNode)value;

      if (!node.isWritable()) {
        append("(Read-only) ", SimpleTextAttributes.ERROR_ATTRIBUTES);
      }

      append(node.toString(), appearsBold(node)
                              ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                              : getMainForegroundAttributes(node));

      if (!node.isValid()) {
        append(" (INVALID)", SimpleTextAttributes.ERROR_ATTRIBUTES);
      }

      int problemCount = node.getProblemCount();
      if (problemCount > 0) {
        if (problemCount == 1) {
          append(" (" + problemCount + " item)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          append(" (" + problemCount + " items)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
      }

      setIcon(node.getIcon(expanded));
      /* if (node instanceof InspectionNode){
         final HighlightDisplayLevel level = myManager.getCurrentProfile().getErrorLevel(HighlightDisplayKey.find(((InspectionNode)node).getTool().getDisplayName()));
         LayeredIcon icon = new LayeredIcon(2);
         icon.setIcon(node.getIcon(expanded), 1);
         icon.setIcon(level.getIcon(),
                      0);
         setIcon(icon);
       }*/
    }

    private SimpleTextAttributes getMainForegroundAttributes(InspectionTreeNode node) {
      SimpleTextAttributes foreground = SimpleTextAttributes.REGULAR_ATTRIBUTES;
      if (node instanceof RefElementNode) {
        RefElement refElement = ((RefElementNode)node).getElement();

        if (refElement instanceof RefClass) {
          RefElement defaultConstructor = ((RefClass)refElement).getDefaultConstructor();
          if (defaultConstructor != null) refElement = defaultConstructor;
        }

        if (refElement.isEntry() && refElement.isPermanentEntry()) {
          foreground = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.blue);
        }
      }
      return foreground;
    }

    private boolean appearsBold(Object node) {
      return ((InspectionTreeNode)node).appearsBold();
    }
  }

  public void sort() {
    sortChildren(getRoot());
  }

  protected void sortChildren(InspectionTreeNode node) {
    TreeUtil.sort(node, RefAlphabeticalComparator.getInstance());
  }

  private class SelectionPath {
    private Object[] myPath;
    private int[] myIndicies;

    public SelectionPath(TreePath path) {
      myPath = path.getPath();
      myIndicies = new int[myPath.length];
      for (int i = 0; i < myPath.length - 1; i++) {
        InspectionTreeNode node = (InspectionTreeNode)myPath[i];
        myIndicies[i + 1] = getChildIndex(node, (InspectionTreeNode)myPath[i + 1]);
      }
    }

    private int getChildIndex(InspectionTreeNode node, InspectionTreeNode child) {
      int idx = 0;
      Enumeration children = node.children();
      while (children.hasMoreElements()) {
        InspectionTreeNode ch = (InspectionTreeNode)children.nextElement();
        if (ch == child) break;
        idx++;
      }
      return idx;
    }

    public void restore() {
      getSelectionModel().removeSelectionPaths(getSelectionModel().getSelectionPaths());
      TreeUtil.selectPath(InspectionTree.this, restorePath());
    }

    private TreePath restorePath() {
      ArrayList<Object> newPath = new ArrayList<Object>();

      newPath.add(getModel().getRoot());
      restorePath(newPath, 1);

      return new TreePath(newPath.toArray(new InspectionTreeNode[newPath.size()]));
    }

    private void restorePath(ArrayList<Object> newPath, int idx) {
      if (idx >= myPath.length) return;
      InspectionTreeNode oldNode = (InspectionTreeNode)myPath[idx];

      InspectionTreeNode newRoot = (InspectionTreeNode)newPath.get(idx - 1);
      if (newRoot instanceof RefElementNode) {
        ((RefElementNode)newRoot).loadChildren();
      }

      RefAlphabeticalComparator comparator = RefAlphabeticalComparator.getInstance();
      Enumeration children = newRoot.children();
      while (children.hasMoreElements()) {
        InspectionTreeNode child = (InspectionTreeNode)children.nextElement();
        if (comparator.compare(child, oldNode) == 0) {
          newPath.add(child);
          restorePath(newPath, idx + 1);
          return;
        }
      }

      // Exactly same element not found. Trying to select somewhat near.
      int count = newRoot.getChildCount();
      if (count > 0) {
        if (myIndicies[idx] < count) {
          newPath.add(newRoot.getChildAt(myIndicies[idx]));
        }
        else {
          newPath.add(newRoot.getChildAt(count - 1));
        }
      }
    }
  }
}
