/*
 * Class AddToWatchAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.debugger.ui.impl.MainWatchPanel;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.debugger.DebuggerInvocationUtil;

public class AddToWatchAction extends DebuggerAction {
  public void actionPerformed(AnActionEvent e) {
    final DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());

    if(debuggerContext == null) return;

    final DebuggerSession session = debuggerContext.getDebuggerSession();
    if(session == null) return;
    final MainWatchPanel watchPanel = DebuggerPanelsManager.getInstance(debuggerContext.getProject()).getWatchPanel();

    if(watchPanel == null) return;

    final DebuggerTreeNodeImpl[] selectedNodes = getSelectedNodes(e.getDataContext());

    if(selectedNodes != null && selectedNodes.length > 0) {
      debuggerContext.getDebugProcess().getManagerThread().invokeLater(new DebuggerContextCommandImpl(debuggerContext) {
        public void threadAction() {
          for (int idx = 0; idx < selectedNodes.length; idx++) {
            DebuggerTreeNodeImpl node = selectedNodes[idx];
            final NodeDescriptorImpl descriptor = node.getDescriptor();
            final TextWithImportsImpl expression = DebuggerTreeNodeExpression.createEvaluationText(node, debuggerContext);
            if (expression != null) {
              DebuggerInvocationUtil.invokeLater(session.getProject(), new Runnable() {
                public void run() {
                  NodeDescriptorImpl watchDescriptor = watchPanel.getWatchTree().addWatch(expression).getDescriptor();
                  watchDescriptor.displayAs(descriptor);
                }
              });
            }
          }
        }

        protected void commandCancelled() {
          DebuggerInvocationUtil.invokeLater(debuggerContext.getProject(), new Runnable() {
            public void run() {
              for (int idx = 0; idx < selectedNodes.length; idx++) {
                DebuggerTreeNodeImpl node = selectedNodes[idx];
                final NodeDescriptorImpl descriptor = node.getDescriptor();

                if(descriptor instanceof WatchItemDescriptor) {

                  final TextWithImportsImpl expression = (TextWithImportsImpl) ((WatchItemDescriptor) descriptor).getEvaluationText();
                  if(expression != null) {
                    NodeDescriptorImpl watchDescriptor = watchPanel.getWatchTree().addWatch(expression).getDescriptor();
                    watchDescriptor.displayAs(descriptor);
                  }
                }
              }
            }
          });
        }
      });
    } else {
      final Editor          editor  = (Editor)e.getDataContext().getData(DataConstants.EDITOR);

      TextWithImportsImpl editorText = DebuggerUtilsEx.getEditorText(editor);

      if(editorText != null) {
        watchPanel.getWatchTree().addWatch(editorText);
      }
    }
  }

  public void update(AnActionEvent e) {
    DebuggerTreeNodeImpl[] selectedNodes = getSelectedNodes(e.getDataContext());
    boolean enabled;
    if (selectedNodes != null) {
      enabled = true;
      if (getPanel(e.getDataContext()) instanceof MainWatchPanel) {
        for (int i = 0; i < selectedNodes.length; i++) {
          DebuggerTreeNodeImpl node = selectedNodes[i];
          NodeDescriptorImpl descriptor = node.getDescriptor();
          if(!(descriptor instanceof ValueDescriptorImpl)) {
            enabled = false;
            break;
          }
        }
      }
    } else {
      final Editor          editor  = (Editor)e.getDataContext().getData(DataConstants.EDITOR);

      enabled = DebuggerUtilsEx.getEditorText(editor) != null ;
    }
    e.getPresentation().setVisible(enabled);
  }
}