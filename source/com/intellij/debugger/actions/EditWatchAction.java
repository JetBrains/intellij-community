package com.intellij.debugger.actions;

import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.ui.impl.MainWatchPanel;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;

/**
 * User: lex
 * Date: Sep 26, 2003
 * Time: 8:34:01 PM
 */
public class EditWatchAction extends DebuggerAction {
  public void actionPerformed(final AnActionEvent e) {
    final DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if(selectedNode == null || !(selectedNode.getDescriptor() instanceof WatchItemDescriptor)) return;

    Project project = (Project) e.getDataContext().getData(DataConstants.PROJECT);

    MainWatchPanel watchPanel = DebuggerPanelsManager.getInstance(project).getWatchPanel();
    if(watchPanel != null) {
      watchPanel.editNode(selectedNode);
    }
  }

  public void update(AnActionEvent e) {
    final DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());

    e.getPresentation().setVisible(selectedNode != null && selectedNode.getDescriptor() instanceof WatchItemDescriptor);
  }

};
