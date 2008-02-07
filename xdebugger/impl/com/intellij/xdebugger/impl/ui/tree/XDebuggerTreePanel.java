package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * @author nik
 */
public class XDebuggerTreePanel {
  private XDebuggerTree myTree;
  private JPanel myMainPanel;
  private PopupHandler myPopupHandler;

  public XDebuggerTreePanel(final Project project, final XDebuggerEditorsProvider editorsProvider, final XSourcePosition sourcePosition,
                            @NotNull @NonNls final String popupActionGroupId) {
    myTree = new XDebuggerTree(project, editorsProvider, sourcePosition);
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
    AnAction action = actionManager.getAction(XDebuggerActions.SET_VALUE);
    action.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)), myTree);
    myTree.addMouseListener(myPopupHandler);
  }

  public void dispose() {
    AnAction action = ActionManager.getInstance().getAction(XDebuggerActions.SET_VALUE);
    action.unregisterCustomShortcutSet(myTree);
    myTree.removeMouseListener(myPopupHandler);
  }

  public XDebuggerTree getTree() {
    return myTree;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }
}
