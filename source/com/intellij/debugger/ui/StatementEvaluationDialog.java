package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.actions.EvaluateAction;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.DimensionService;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 12, 2004
 * Time: 4:28:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class StatementEvaluationDialog extends EvaluationDialog{
  private JPanel myPanel;
  private final Action mySwitchAction = new SwitchAction();
  private static final String STATEMENT_EDITOR_DIMENSION_KEY = "#com.intellij.debugger.ui.StatementEvaluationDialog.StatementEditor";
  private static final String EVALUATION_PANEL_DIMENSION_KEY = "#com.intellij.debugger.ui.StatementEvaluationDialog.EvaluationPanel";

  public StatementEvaluationDialog(final Project project, TextWithImports text) {
    super(project, text);
    setTitle("Code Fragment Evaluation");
    myPanel = new JPanel(new BorderLayout());

    final Splitter splitter = new Splitter(true);
    splitter.setHonorComponentsMinimumSize(true);
    final DebuggerStatementEditor statementEditor = getStatementEditor();
    splitter.setFirstComponent(statementEditor);
    final EvaluationDialog.MyEvaluationPanel evaluationPanel = getEvaluationPanel();
    splitter.setSecondComponent(evaluationPanel);
    final Dimension statementSize = DimensionService.getInstance().getSize(STATEMENT_EDITOR_DIMENSION_KEY);
    final Dimension evaluationSize = DimensionService.getInstance().getSize(EVALUATION_PANEL_DIMENSION_KEY);
    if (statementSize != null && evaluationSize != null) {
      final float proportion = (float)statementSize.height / (float)(statementSize.height + evaluationSize.height);
      splitter.setProportion(proportion);
    }
    myPanel.add(splitter, BorderLayout.CENTER);

    setDebuggerContext(getDebuggerContext());

    KeyStroke codeFragment = KeyStroke.getKeyStroke(KeyEvent.VK_E,     KeyEvent.ALT_MASK);
    KeyStroke resultStroke = KeyStroke.getKeyStroke(KeyEvent.VK_R,     KeyEvent.ALT_MASK);
    KeyStroke altEnter     = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_MASK);

    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        getStatementEditor().requestFocus();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(codeFragment), getRootPane());

    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        getEvaluationPanel().getWatchTree().requestFocus();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(resultStroke), getRootPane());

    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        doOKAction();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(altEnter), getRootPane());

    getEditor().addDocumentListener(new DocumentAdapter() {
      public void documentChanged(final DocumentEvent e) {
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            updateSwitchButton(e.getDocument());
          }
        });
      }
    });

    this.init();
  }

  private void updateSwitchButton(Document document) {
    PsiDocumentManager.getInstance(getProject()).commitDocument(document);
    PsiFile psiFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
    PsiElement[] children = psiFile.getChildren();
    int nonWhite = 0;
    for (PsiElement child : children) {
      if (!(child instanceof PsiWhiteSpace)) {
        nonWhite++;
        if (nonWhite > 1) {
          mySwitchAction.setEnabled(false);
          return;
        }
      }
    }

    mySwitchAction.setEnabled(true);
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(), getCancelAction(), mySwitchAction };
  }

  protected DebuggerEditorImpl createEditor() {
    return new DebuggerStatementEditor(getProject(), PositionUtil.getContextElement(getDebuggerContext()), "evaluation");
  }

  protected void dispose() {
    try {
      final DebuggerEditorImpl editor = getEditor();
      final DimensionService dimensionService = DimensionService.getInstance();
      dimensionService.setSize(STATEMENT_EDITOR_DIMENSION_KEY, editor.getSize(null));
      dimensionService.setSize(EVALUATION_PANEL_DIMENSION_KEY, getEvaluationPanel().getSize());
    }
    finally {
      super.dispose();
    }
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private DebuggerStatementEditor getStatementEditor() {
    return (DebuggerStatementEditor)getEditor();
  }

  private class SwitchAction extends AbstractAction {
    public SwitchAction() {
      putValue(Action.NAME, "Expression Mode");
    }

    public void actionPerformed(ActionEvent e) {
      final TextWithImports text = getEditor().getText();
      doCancelAction();
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        public void run() {
          EvaluateAction.showEvaluationDialog(getProject(), text, DebuggerSettings.EVALUATE_EXPRESSION);
        }
      });
    }
  }


}
