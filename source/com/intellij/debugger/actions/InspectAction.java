/*
 * Class InspectAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.expression.Modifier;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.InspectDialog;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.sun.jdi.Field;

public class InspectAction extends DebuggerAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    final DebuggerTreeNodeImpl node = getSelectedNode(e.getDataContext());
    if(node == null) return;
    final NodeDescriptorImpl descriptor = node.getDescriptor();
    final DebuggerStateManager stateManager = getContextManager(e.getDataContext());
    if(!(descriptor instanceof ValueDescriptorImpl) || stateManager == null) return;
    final DebuggerContextImpl context = stateManager.getContext();

    if (!canInspect((ValueDescriptorImpl)descriptor, context)) {
      return;
    }

    context.getDebugProcess().getManagerThread().invokeLater(new DebuggerContextCommandImpl(context) {
      public void threadAction() {
        final TextWithImportsImpl evaluationText = DebuggerTreeNodeExpression.createEvaluationText(node, context);

        final NodeDescriptorImpl inspectDescriptor;

        if (descriptor instanceof WatchItemDescriptor) {
          inspectDescriptor = (NodeDescriptorImpl) ((WatchItemDescriptor) descriptor).getModifier().getInspectItem(project);
        } else {
          inspectDescriptor = descriptor;
        }

        DebuggerInvocationUtil.invokeLater(project, new Runnable() {
          public void run() {
            InspectDialog dialog = new InspectDialog(project,
                    stateManager,
                    "Inspect '" + evaluationText + "'",
                    inspectDescriptor);
            dialog.show();
          }
        });
      }
    });
  }

  private boolean canInspect(ValueDescriptorImpl descriptor, DebuggerContextImpl context) {
    DebuggerSession session = context.getDebuggerSession();
    if (session == null || !session.isPaused()) return false;

    boolean isField = descriptor instanceof FieldDescriptorImpl;

    if(descriptor instanceof WatchItemDescriptor) {
      Modifier modifier = ((WatchItemDescriptor)descriptor).getModifier();
      if(modifier == null || !modifier.canInspect()) return false;
      isField = modifier instanceof Field;
    }

    if (isField) { // check if possible
      if (!context.getDebugProcess().canWatchFieldModification()) {
        Messages.showMessageDialog(
          context.getProject(),
          "Cannot inspect: Target VM does not support modification watchpoints",
          "Inspect",
          Messages.getInformationIcon()
        );
        return false;
      }
    }
    return true;
  }

  public void update(AnActionEvent e) {
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    boolean enabled = false;
    if(selectedNode != null) {
      NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
      if(descriptor != null) {
        if(descriptor instanceof LocalVariableDescriptorImpl || descriptor instanceof FieldDescriptorImpl || descriptor instanceof ArrayElementDescriptorImpl) {
          enabled = true;
        }
        else if(descriptor instanceof WatchItemDescriptor){
          Modifier modifier = ((WatchItemDescriptor)descriptor).getModifier();
          enabled = modifier != null && modifier.canInspect();
        }
      }
    }
    e.getPresentation().setVisible(enabled);
  }

}