package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.Tree;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * @author nik
 */
public class XDebuggerTreePanel implements DnDSource {
  private XDebuggerTree myTree;
  private JPanel myMainPanel;
  private PopupHandler myPopupHandler;

  public XDebuggerTreePanel(final XDebugSession session, final XDebuggerEditorsProvider editorsProvider, final XSourcePosition sourcePosition,
                            @NotNull @NonNls final String popupActionGroupId) {
    myTree = new XDebuggerTree(session, editorsProvider, sourcePosition);
    myMainPanel = new JPanel(new BorderLayout());
    myMainPanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    final ActionManager actionManager = ActionManager.getInstance();
    myPopupHandler = new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        final ActionGroup group = (ActionGroup)actionManager.getAction(popupActionGroupId);
        ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, group);
        popupMenu.getComponent().show(comp, x, y);
      }
    };
    AnAction setValueAction = actionManager.getAction(XDebuggerActions.SET_VALUE);
    setValueAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)), myTree);
    AnAction jumpToSourceAction = actionManager.getAction(XDebuggerActions.JUMP_TO_SOURCE);
    jumpToSourceAction.registerCustomShortcutSet(actionManager.getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet(), myTree);
    myTree.addMouseListener(myPopupHandler);
  }

  public void dispose() {
    ActionManager actionManager = ActionManager.getInstance();
    actionManager.getAction(XDebuggerActions.SET_VALUE).unregisterCustomShortcutSet(myTree);
    actionManager.getAction(XDebuggerActions.JUMP_TO_SOURCE).unregisterCustomShortcutSet(myTree);
    myTree.removeMouseListener(myPopupHandler);
  }

  public XDebuggerTree getTree() {
    return myTree;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public boolean canStartDragging(final DnDAction action, final Point dragOrigin) {
    return getNodesToDrag().length > 0;
  }

  private XValueNodeImpl[] getNodesToDrag() {
    return myTree.getSelectedNodes(XValueNodeImpl.class, new Tree.NodeFilter<XValueNodeImpl>() {
      public boolean accept(final XValueNodeImpl node) {
        return node.getValueContainer().getEvaluationExpression() != null;
      }
    });
  }

  public DnDDragStartBean startDragging(final DnDAction action, final Point dragOrigin) {
    return new DnDDragStartBean(getNodesToDrag());
  }

  public Pair<Image, Point> createDraggedImage(final DnDAction action, final Point dragOrigin) {
    XValueNodeImpl[] nodes = getNodesToDrag();
    if (nodes.length == 1) {
      return DnDAwareTree.getDragImage(myTree, nodes[0].getPath(), dragOrigin);
    }
    return DnDAwareTree.getDragImage(myTree, XDebuggerBundle.message("xdebugger.drag.text.0.elements", nodes.length), dragOrigin);
  }

  public void dragDropEnd() {
  }

  public void dropActionChanged(final int gestureModifiers) {
  }
}
