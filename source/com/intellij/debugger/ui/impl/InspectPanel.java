/*
 * Class InspectPanel
 * @author Jeka
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;

public class InspectPanel extends DebuggerPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.InspectPanel");

  public InspectPanel(Project project, DebuggerStateManager stateManager, NodeDescriptorImpl inspectDescriptor) {
    super(project, stateManager);
    LOG.assertTrue(inspectDescriptor != null);

    getInspectTree().setInspectDescriptor(inspectDescriptor);

    add(new JScrollPane(getInspectTree()), BorderLayout.CENTER);
    DebuggerAction.installEditAction(getInspectTree(), DebuggerActions.EDIT_NODE_SOURCE);
  }


  protected DebuggerTree createTreeView() {
    return new InspectDebuggerTree(getProject());
  }

  protected ActionPopupMenu createPopupMenu() {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(DebuggerActions.INSPECT_PANEL_POPUP);
    ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(DebuggerActions.INSPECT_PANEL_POPUP, group);
    return popupMenu;
  }

  public InspectDebuggerTree getInspectTree() {
    return (InspectDebuggerTree)getTree();
  }
}