package com.intellij.debugger.actions;

import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.render.ArrayRenderer;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

public class AdjustArrayRangeAction extends DebuggerAction {
  public void actionPerformed(AnActionEvent e) {
    DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
    if(debuggerContext == null) return;

    DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    if(debugProcess == null) return;

    Project project = debuggerContext.getProject();

    final DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if (selectedNode == null) {
      return;
    }
    NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
    if(!(descriptor instanceof ValueDescriptorImpl && ((ValueDescriptorImpl)descriptor).isArray())) return;

    ArrayRenderer renderer = (ArrayRenderer)((ValueDescriptorImpl)selectedNode.getDescriptor()).getLastRenderer();

    String title = createNodeTitle("", selectedNode);
    String label = selectedNode.toString();
    int index = label.indexOf('=');
    if (index > 0) {
      title = title + " " + label.substring(index);
    }
    final ArrayRenderer cloneRenderer = renderer.clone();
    AdjustRangeDialog dialog = new AdjustRangeDialog(project, title, cloneRenderer);
    dialog.show();
    
    if(dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      debugProcess.getManagerThread().invokeLater(new SuspendContextCommandImpl(debuggerContext.getSuspendContext()) {
        public void contextAction() throws Exception {
          selectedNode.setRenderer(cloneRenderer);
        }
      });
    }
  }

  public void update(AnActionEvent e) {
    boolean enable = false;
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if(selectedNode != null) {
      NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
      enable = descriptor instanceof ValueDescriptorImpl && ((ValueDescriptorImpl)descriptor).isArray() && ((ValueDescriptorImpl) descriptor).getLastRenderer() instanceof ArrayRenderer;
    }
    e.getPresentation().setVisible(enable);
  }

  private static String createNodeTitle(String prefix, DebuggerTreeNodeImpl node) {
    if (node != null) {
      DebuggerTreeNodeImpl parent = (DebuggerTreeNodeImpl)node.getParent();
      NodeDescriptorImpl descriptor = parent.getDescriptor();
      if (descriptor instanceof ValueDescriptorImpl && ((ValueDescriptorImpl)descriptor).isArray()) {
        int index = parent.getIndex(node);
        return createNodeTitle(prefix, parent) + "[" + index + "]";
      }
      String name = (node.getDescriptor() != null)? node.getDescriptor().getName() : null;
      return (name != null)? prefix + " " + name : prefix;
    }
    return prefix;
  }
}