package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.impl.DebuggerContextUtil;
import com.intellij.debugger.impl.DebuggerContextUtil;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class ThreadsPanel extends DebuggerPanel implements DataProvider {
  public ThreadsPanel(Project project, DebuggerStateManager stateManager) {
    super(project, stateManager);

    DebuggerAction.installEditAction(getThreadsTree(), DebuggerActions.EDIT_FRAME_SOURCE);

    getThreadsTree().addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && getThreadsTree().getSelectionCount() == 1) {
          DebuggerTreeNodeImpl node = (DebuggerTreeNodeImpl)getThreadsTree().getLastSelectedPathComponent();
          if (node != null) {
            NodeDescriptorImpl descriptor = node.getDescriptor();
            if (descriptor instanceof StackFrameDescriptorImpl) {
              selectFrame(node);
            }
          }
        }
      }
    });
    add(new JScrollPane(getThreadsTree()), BorderLayout.CENTER);
  }

  protected DebuggerTree createTreeView() {
    return new ThreadsDebuggerTree(getProject());
  }

  protected ActionPopupMenu createPopupMenu() {
    DefaultActionGroup group = (DefaultActionGroup)ActionManager.getInstance().getAction(DebuggerActions.THREADS_PANEL_POPUP);
    ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(DebuggerActions.THREADS_PANEL_POPUP, group);
    return popupMenu;
  }

  public Object getData(String dataId) {
    if (DataConstantsEx.HELP_ID.equals(dataId)) {
      return "debugging.debugThreads";
    }
    return super.getData(dataId);
  }

  private void selectFrame(DebuggerTreeNodeImpl node) {
    StackFrameProxyImpl frame = ((StackFrameDescriptorImpl)node.getDescriptor()).getStackFrame();
    DebuggerContextUtil.setStackFrame(getContextManager(), frame);
  }

  public ThreadsDebuggerTree getThreadsTree() {
    return (ThreadsDebuggerTree) getTree();
  }
}