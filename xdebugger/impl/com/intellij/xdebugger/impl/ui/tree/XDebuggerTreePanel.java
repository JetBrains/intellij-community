package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.project.Project;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

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
    myPopupHandler = new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        ActionManager actionManager = ActionManager.getInstance();
        final ActionGroup group = (ActionGroup)actionManager.getAction(popupActionGroupId);
        ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, group);
        popupMenu.getComponent().show(comp, x, y);
      }
    };
    myTree.addMouseListener(myPopupHandler);
  }

  public void dispose() {
    myTree.removeMouseListener(myPopupHandler);
  }

  public XDebuggerTree getTree() {
    return myTree;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }
}
