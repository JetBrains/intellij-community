/*
 * Class WatchPanel
 * @author Jeka
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;
import java.util.Enumeration;

public abstract class WatchPanel extends DebuggerPanel {
  public WatchPanel(Project project, DebuggerStateManager stateManager) {
    super(project, stateManager);
    add(new JScrollPane(getWatchTree()), BorderLayout.CENTER);
    DebuggerAction.installEditAction(getWatchTree(), DebuggerActions.EDIT_NODE_SOURCE);
  }

  protected DebuggerTree createTreeView() {
    return new WatchDebuggerTree(getProject());
  }

  protected void changeEvent(DebuggerContextImpl newContext, int event) {
    if(event == DebuggerSession.EVENT_ATTACHED) {
      DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl) getWatchTree().getModel().getRoot();
      if(root != null) {
        for(Enumeration e = root.rawChildren(); e.hasMoreElements();) {
          DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl) e.nextElement();
          ((WatchItemDescriptor) child.getDescriptor()).setNew();
        }
      }
    }

    rebuildWhenVisible(event);
  }

  protected ActionPopupMenu createPopupMenu() {
    return null;
  }

  public Object getData(String dataId) {
    if (DataConstantsEx.HELP_ID.equals(dataId)) {
      return "debugging.debugWatches";
    }
    return super.getData(dataId);
  }

  public WatchDebuggerTree getWatchTree() {
    return (WatchDebuggerTree) getTree();
  }
}