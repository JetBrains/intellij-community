package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * User: lex
 * Date: Sep 26, 2003
 * Time: 7:35:09 PM
 */
public class ResumeThreadAction extends DebuggerAction{
  public void actionPerformed(final AnActionEvent e) {
    DebuggerTreeNodeImpl[] selectedNode = getSelectedNodes(e.getDataContext());
    final DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
    final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();


    for (int i = 0; i < selectedNode.length; i++) {
      final DebuggerTreeNodeImpl debuggerTreeNode = selectedNode[i];
      ThreadDescriptorImpl threadDescriptor = ((ThreadDescriptorImpl)debuggerTreeNode.getDescriptor());
      final ThreadReferenceProxyImpl thread = threadDescriptor.getThreadReference();

      if(threadDescriptor.isSuspended()) {
        debugProcess.getManagerThread().invokeLater(new SuspendContextCommandImpl(debuggerContext.getSuspendContext()) {
          public void contextAction() throws Exception {
            debugProcess.createResumeThreadCommand(getSuspendContext(), thread).run();
            debuggerTreeNode.calcValue();
          }
        });
      }
    }
  }

  public void update(AnActionEvent e) {
    DebuggerTreeNodeImpl[] selectedNode = getSelectedNodes(e.getDataContext());

    boolean visible = false;

    if(selectedNode.length > 0){
      visible = true;
      for (int i = 0; i < selectedNode.length; i++) {
        NodeDescriptorImpl threadDescriptor = selectedNode[i].getDescriptor();
        if(!(threadDescriptor instanceof ThreadDescriptorImpl) ||
           !((ThreadDescriptorImpl)threadDescriptor).isSuspended()) {
          visible = false;
          break;
        }
      }
    }
    e.getPresentation().setVisible(visible);
  }
}
