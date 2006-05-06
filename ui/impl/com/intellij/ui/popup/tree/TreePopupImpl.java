/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup.tree;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.TreePopup;
import com.intellij.openapi.ui.popup.TreePopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.BasePopup;
import com.intellij.ui.treeStructure.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class TreePopupImpl extends BasePopup implements TreePopup {

  private MyTree myWizardTree;
  private Dimension myPreferredSize = new Dimension(0, 0);

  private MouseMotionListener myMouseMotionListener;
  private MouseListener myMouseListener;

  private List<TreePath> mySavedExpanded = new ArrayList<TreePath>();
  private TreePath mySavedSelected;

  private TreePath myShowingChildPath;
  private TreePath myPendingChildPath;
  private MyTreeBuilder myBuilder;

  public TreePopupImpl(JBPopup parent, TreePopupStep aStep, Object parentValue) {
    super(parent, aStep);
    setParentValue(parentValue);
  }

  public TreePopupImpl(TreePopupStep aStep) {
    this(null, aStep, null);
  }

  protected JComponent createContent() {
    myWizardTree = new MyTree();
    myWizardTree.getAccessibleContext().setAccessibleName("WizardTree");
    myBuilder = new MyTreeBuilder();

    myBuilder.updateFromRoot();

    myWizardTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    Action action = myWizardTree.getActionMap().get("toggleSelectionPreserveAnchor");
    if (action != null) {
      action.setEnabled(false);
    }

    myWizardTree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
          toggleExpansion(myWizardTree.getAnchorSelectionPath());
        }
      }
    });

    myWizardTree.setRootVisible(getTreeStep().isRootVisible());
    myWizardTree.setShowsRootHandles(true);

    ToolTipManager.sharedInstance().registerComponent(myWizardTree);
    myWizardTree.setCellRenderer(new MyRenderer());

    myMouseMotionListener = new MyMouseMotionListener();
    myMouseListener = new MyMouseListener();

    registerAction("select", KeyEvent.VK_ENTER, 0, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        handleSelect(true);
      }
    });

    registerAction("toggleExpansion", KeyEvent.VK_SPACE, 0, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        toggleExpansion(myWizardTree.getSelectionPath());
      }
    });

    final Action oldExpandAction = getActionMap().get("selectChild");
    getActionMap().put("selectChild", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        final TreePath path = myWizardTree.getSelectionPath();
        if (path != null && 0 == myWizardTree.getModel().getChildCount(path.getLastPathComponent())) {
          handleSelect(false);
          return;
        }
        oldExpandAction.actionPerformed(e);
      }
    });

    final Action oldCollapseAction = getActionMap().get("selectParent");
    getActionMap().put("selectParent", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        final TreePath path = myWizardTree.getSelectionPath();
        if (shouldHidePopup(path)) {
          goBack();
          return;
        }
        oldCollapseAction.actionPerformed(e);
      }
    });

    return myWizardTree;
  }

  private boolean shouldHidePopup(TreePath path) {
    if (getParent() == null) return false;
    if (path == null) return false;
    if (!myWizardTree.isCollapsed(path)) return false;
    if (myWizardTree.isRootVisible()) {
      return path.getPathCount() == 1;
    }
    return path.getPathCount() == 2;
  }

  protected ActionMap getActionMap() {
    return myWizardTree.getActionMap();
  }

  protected InputMap getInputMap() {
    return myWizardTree.getInputMap();
  }

  private void addListeners() {
    myWizardTree.addMouseMotionListener(myMouseMotionListener);
    myWizardTree.addMouseListener(myMouseListener);
  }

  protected void dispose() {
    mySavedExpanded.clear();
    final Enumeration<TreePath> expanded = myWizardTree.getExpandedDescendants(new TreePath(myWizardTree.getModel().getRoot()));
    if (expanded != null) {
      while (expanded.hasMoreElements()) {
        mySavedExpanded.add(expanded.nextElement());
      }
    }
    mySavedSelected = myWizardTree.getSelectionPath();

    myWizardTree.removeMouseMotionListener(myMouseMotionListener);
    myWizardTree.removeMouseListener(myMouseListener);
    super.dispose();
  }

  protected Dimension getContentPreferredSize() {
    return myPreferredSize;
  }

  protected void beforeShow() {
    addListeners();

    expandAll();

    myPreferredSize = new Dimension(myWizardTree.getPreferredSize());
    myPreferredSize.width += 10;

    collapseAll();

    restoreExpanded();
    if (mySavedSelected != null) {
      myWizardTree.setSelectionPath(mySavedSelected);
    }
  }

  protected void afterShow() {
    selectFirstSelectableItem();
  }

  // TODO: not-tested code:
  private void selectFirstSelectableItem() {
    for (int i = 0; i < myWizardTree.getRowCount(); i++) {
      TreePath path = myWizardTree.getPathForRow(i);
      if (getTreeStep().isSelectable(path.getLastPathComponent(), extractUserObject(path.getLastPathComponent()))) {
        myWizardTree.setSelectionPath(path);
        break;
      }
    }
  }


  private void restoreExpanded() {
    if (mySavedExpanded.size() == 0) {
      expandAll();
      return;
    }

    for (TreePath each : mySavedExpanded) {
      myWizardTree.expandPath(each);
    }
  }

  private void expandAll() {
    for (int i = 0; i < myWizardTree.getRowCount(); i++) {
      myWizardTree.expandRow(i);
    }
  }

  private void collapseAll() {
    int row = myWizardTree.getRowCount() - 1;
    while (row > 0) {
      myWizardTree.collapseRow(row);
      row--;
    }
  }

  private TreePopupStep getTreeStep() {
    return (TreePopupStep) myStep;
  }

  private class MyMouseMotionListener extends MouseMotionAdapter {
    public void mouseMoved(MouseEvent e) {
      final TreePath path = getPath(e);
      if (path != null) {
        myWizardTree.setSelectionPath(path);
        notifyParentOnChildSelection();
        if (getTreeStep().isSelectable(path.getLastPathComponent(), extractUserObject(path.getLastPathComponent()))) {
          myWizardTree.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          if (myPendingChildPath == null || !myPendingChildPath.equals(path)) {
            myPendingChildPath = path;
            restartTimer();
          }
          return;
        }
      }
      myWizardTree.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

  }

  private TreePath getPath(MouseEvent e) {
    return myWizardTree.getClosestPathForLocation(e.getPoint().x, e.getPoint().y);
  }

  private class MyMouseListener extends MouseAdapter {

    public void mousePressed(MouseEvent e) {
      final TreePath path = getPath(e);
      if (path == null) {
        return;
      }

      if (e.getButton() != MouseEvent.BUTTON1) {
        return;
      }

      final Object selected = path.getLastPathComponent();

      if (getTreeStep().isSelectable(selected, extractUserObject(selected))) {
        handleSelect(true);
      }
      else {
        if (!isLocationInExpandControl(myWizardTree, path, e.getPoint().x, e.getPoint().y)) {
          toggleExpansion(path);
        }
      }
    }

    public void mouseReleased(MouseEvent e) {
    }

  }

  private void toggleExpansion(TreePath path) {
    if (path == null) {
      return;
    }

    if (getTreeStep().isSelectable(path.getLastPathComponent(), extractUserObject(path.getLastPathComponent()))) {
      if (myWizardTree.isExpanded(path)) {
        myWizardTree.collapsePath(path);
      }
      else {
        myWizardTree.expandPath(path);
      }
    }
  }

  private void handleSelect(boolean handleFinalChoices) {
    final boolean pathIsAlreadySelected = myShowingChildPath != null && myShowingChildPath.equals(myWizardTree.getSelectionPath());
    if (pathIsAlreadySelected) return;

    myPendingChildPath = null;

    Object selected = myWizardTree.getLastSelectedPathComponent();
    if (selected != null) {
      final Object userObject = extractUserObject(selected);
      if (getTreeStep().isSelectable(selected, userObject)) {
        disposeChildren();

        final boolean hasNextStep = myStep.hasSubstep(userObject);
        if (!hasNextStep && !handleFinalChoices) {
          myShowingChildPath = null;
          return;
        }

        final PopupStep queriedStep = myStep.onChosen(userObject, handleFinalChoices);
        if (queriedStep == PopupStep.FINAL_CHOICE || !hasNextStep) {
          disposeAllParents();
        }
        else {
          myShowingChildPath = myWizardTree.getSelectionPath();
          handleNextStep(queriedStep, myShowingChildPath);
          myShowingChildPath = null;
        }
      }
    }
  }

  private void handleNextStep(PopupStep nextStep, Object parentValue) {
    final Rectangle pathBounds = myWizardTree.getPathBounds(myWizardTree.getSelectionPath());
    final Point point = new RelativePoint(myWizardTree, new Point(myContainer.getWidth() + 2, (int) pathBounds.getY())).getScreenPoint();
    myChild = createPopup(this, nextStep, parentValue);
    myChild.show(getContainer(), point.x - STEP_X_PADDING, point.y);
  }

  private class MyRenderer extends SimpleNodeRenderer {

    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      final boolean shouldPaintSelected = (getTreeStep().isSelectable(value, extractUserObject(value)) && selected) || (getTreeStep().isSelectable(value, extractUserObject(value)) && hasFocus);
      final boolean shouldPaintFocus = (!getTreeStep().isSelectable(value, extractUserObject(value)) && selected)
          || (shouldPaintSelected)
          || (hasFocus);

      super.customizeCellRenderer(tree, value, shouldPaintSelected, expanded, leaf, row, shouldPaintFocus);
    }
  }

  public static boolean isLocationInExpandControl(JTree aTree, TreePath path, int mouseX, int mouseY) {
    TreeModel treeModel = aTree.getModel();

    final BasicTreeUI basicTreeUI = ((BasicTreeUI) aTree.getUI());
    Icon expandedIcon = basicTreeUI.getExpandedIcon();

    if (path != null && !treeModel.isLeaf(path.getLastPathComponent())) {
      int boxWidth;
      Insets i = aTree.getInsets();

      if (expandedIcon != null) {
        boxWidth = expandedIcon.getIconWidth();
      }
      else {
        boxWidth = 8;
      }

      int boxLeftX = (i != null) ? i.left : 0;

      boolean leftToRight = aTree.getComponentOrientation().isLeftToRight();
      int depthOffset = getDepthOffset(aTree);
      int totalChildIndent = basicTreeUI.getLeftChildIndent() + basicTreeUI.getRightChildIndent();

      if (leftToRight) {
        boxLeftX += (((path.getPathCount() + depthOffset - 2) *
            totalChildIndent) + basicTreeUI.getLeftChildIndent()) -
            boxWidth / 2;
      }
      int boxRightX = boxLeftX + boxWidth;

      return mouseX >= boxLeftX && mouseX <= boxRightX;
    }
    return false;
  }

  private static int getDepthOffset(JTree aTree) {
    if (aTree.isRootVisible()) {
      if (aTree.getShowsRootHandles()) {
        return 1;
      }
      else {
        return 0;
      }
    }
    else if (!aTree.getShowsRootHandles()) {
      return -1;
    }
    else {
      return 0;
    }
  }

  protected void process(KeyEvent aEvent) {
    myWizardTree.processKeyEvent(aEvent);
  }

  protected Object extractUserObject(Object aNode) {
    Object object = ((DefaultMutableTreeNode) aNode).getUserObject();
    if (object instanceof TreePopupStructure.Node) {
      return ((TreePopupStructure.Node) object).getDelegate();
    }
    return object;
  }

  private class MyTree extends SimpleTree {
    public void processKeyEvent(KeyEvent e) {
      e.setSource(this);
      super.processKeyEvent(e);
    }

    @Override
    public Dimension getPreferredSize() {
      final Dimension pref = super.getPreferredSize();
      return new Dimension(pref.width + 10, pref.height);
    }

    protected void paintChildren(Graphics g) {
      super.paintChildren(g);

      Rectangle visibleRect = getVisibleRect();
      int rowForLocation = getClosestRowForLocation(0, visibleRect.y);
      for (int i = rowForLocation; i < rowForLocation + getVisibleRowCount() + 1; i++) {
        final TreePath eachPath = getPathForRow(i);
        if (eachPath == null) continue;

        final Object lastPathComponent = eachPath.getLastPathComponent();
        final boolean hasNextStep = getTreeStep().hasSubstep(extractUserObject(lastPathComponent));
        if (!hasNextStep) continue;

        Icon icon = isPathSelected(eachPath) ?
                    IconLoader.getIcon("/icons/ide/nextStep.png") :
                    IconLoader.getIcon("/icons/ide/nextStepGrayed.png");
        final Rectangle rec = getPathBounds(eachPath);
        int x = getSize().width - icon.getIconWidth() - 1;
        int y = rec.y + (rec.height - icon.getIconWidth()) / 2;
        icon.paintIcon(this, g, x, y);
      }
    }
  }

  public Project getProject() {
    return getTreeStep().getProject();
  }

  private class MyTreeBuilder extends AbstractTreeBuilder {

    private Object myLastSuccessfulSelect;

    MyTreeBuilder() {
      super(myWizardTree, (DefaultTreeModel) myWizardTree.getModel(), new TreePopupStructure(getProject(), TreePopupImpl.this, getTreeStep().getStructure()), AlphaComparator.INSTANCE);
      initRootNode();
    }

    public boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
      return false;
    }

    public boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
      return true;
    }

    protected final DefaultMutableTreeNode createChildNode(final NodeDescriptor childDescr) {
      return new PatchedDefaultMutableTreeNode(childDescr);
    }

    public void refilter() {
      Object selectedObject = getSelected();
      final Object toSelect = getTreeStep().isSelectable(selectedObject, selectedObject) ? selectedObject : null;

      ((TreePopupStructure) getTreeStructure()).refilter();
      updateFromRoot();

      boolean wasSelected = false;
      if (selectedObject != null) {
        wasSelected = myWizardTree.select(this, new SimpleNodeVisitor() {
          public boolean accept(SimpleNode simpleNode) {
            if (simpleNode instanceof TreePopupStructure.Node) {
              TreePopupStructure.Node node = ((TreePopupStructure.Node) simpleNode);
              return node.getDelegate().equals(toSelect);
            }
            else {
              return false;
            }
          }
        }, true);
      }

      if (!wasSelected) {
        myWizardTree.select(this, new SimpleNodeVisitor() {
          public boolean accept(SimpleNode simpleNode) {
            if (simpleNode instanceof TreePopupStructure.Node) {
              TreePopupStructure.Node node = ((TreePopupStructure.Node) simpleNode);
              if (getTreeStep().isSelectable(node, node.getDelegate())) {
                return true;
              }
            }
            else {
              return false;
            }
            return false;
          }
        }, true);
      }

      if (!wasSelected && myLastSuccessfulSelect != null) {
        wasSelected = myWizardTree.select(this, new SimpleNodeVisitor() {
          public boolean accept(SimpleNode simpleNode) {
            if (simpleNode instanceof TreePopupStructure.Node) {
              Object object = ((TreePopupStructure.Node) simpleNode).getDelegate();
              return myLastSuccessfulSelect.equals(object);
            }
            return false;
          }
        }, true);
        if (wasSelected) {
          myLastSuccessfulSelect = getSelected();
        }
      } else if (wasSelected) {
        myLastSuccessfulSelect = getSelected();
      }

    }

    @Nullable
    private Object getSelected() {
      TreePopupStructure.Node selected = (TreePopupStructure.Node) myWizardTree.getSelectedNode();
      return selected != null ? selected.getDelegate() : null;
    }

    @NotNull
    protected ProgressIndicator createProgressIndicator() {
      return new StatusBarProgress();
    }
  }

  protected void onAutoSelectionTimer() {
    handleSelect(false);
  }

  protected void requestFocus() {
    myWizardTree.requestFocus();
  }

  protected void onSpeedSearchPatternChanged() {
    myBuilder.refilter();
  }

  protected void onChildSelectedFor(Object value) {
    TreePath path = (TreePath) value;
    if (myWizardTree.getSelectionPath() != path) {
      myWizardTree.setSelectionPath(path);
    }
  }

  }
