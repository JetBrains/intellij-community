/*
 * Class EvaluateAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.ExpressionEvaluationDialog;
import com.intellij.debugger.ui.StatementEvaluationDialog;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

public class EvaluateAction extends DebuggerAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext   dataContext = e.getDataContext();
    final Project project     = (Project)dataContext.getData(DataConstants.PROJECT);

    final DebuggerContextImpl context = DebuggerAction.getDebuggerContext(dataContext);

    if(project == null || context == null) {
      return;
    }

    final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);

    TextWithImports editorText = DebuggerUtilsEx.getEditorText(editor);
    if (editorText == null) {
      final DebuggerTreeNodeImpl selectedNode = DebuggerAction.getSelectedNode(dataContext);

      if (selectedNode != null && selectedNode.getDescriptor() instanceof ValueDescriptorImpl) {
        context.getDebugProcess().getManagerThread().invokeLater(new DebuggerContextCommandImpl(context) {
          public void threadAction() {
            final TextWithImports evaluationText = DebuggerTreeNodeExpression.createEvaluationText(selectedNode, context);
            DebuggerInvocationUtil.invokeLater(project, new Runnable() {
              public void run() {
                showEvaluationDialog(project, evaluationText);
              }
            });
          }

          protected void commandCancelled() {
            DebuggerInvocationUtil.invokeLater(project, new Runnable() {
              public void run() {
                if(selectedNode.getDescriptor() instanceof WatchItemDescriptor) {
                  TextWithImports editorText = DebuggerTreeNodeExpression.createEvaluationText(selectedNode, context);
                  showEvaluationDialog(project, editorText);
                }
              }
            });
          }
        });
        return;
      }
    }

    showEvaluationDialog(project, editorText);
  }

  public static void showEvaluationDialog(Project project, TextWithImports defaultExpression, String dialogType) {
    if(defaultExpression == null) {
      defaultExpression = new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "");
    }

    final DialogWrapper dialog;
    DebuggerSettings.getInstance().EVALUATION_DIALOG_TYPE = dialogType;
    if(DebuggerSettings.EVALUATE_FRAGMENT.equals(dialogType)) {
      dialog = new StatementEvaluationDialog(project, defaultExpression);
    } 
    else {
      dialog = new ExpressionEvaluationDialog(project, defaultExpression);
    }

    dialog.show();
  }

  public static void showEvaluationDialog(Project project, TextWithImports text) {
    showEvaluationDialog(project, text, DebuggerSettings.getInstance().EVALUATION_DIALOG_TYPE);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    DebuggerContextImpl context = getDebuggerContext(event.getDataContext());

    boolean toEnable = false;

    if(context != null) {
      DebuggerSession debuggerSession = context.getDebuggerSession();

      toEnable = debuggerSession != null && debuggerSession.isPaused();
    }

    presentation.setEnabled(toEnable);
    if (ActionPlaces.EDITOR_POPUP.equals(event.getPlace())) {
      presentation.setVisible(toEnable);
    }
    else {
      presentation.setVisible(true);
    }
  }
}
