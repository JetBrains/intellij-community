package com.intellij.debugger.ui;

import com.intellij.debugger.actions.EvaluateAction;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.ui.GuiUtils;
import com.intellij.debugger.DebuggerInvocationUtil;

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
  private JPanel myWatchViewPlace;
  private JPanel myStatementEditorPlace;
  private JPanel myPanel;
  private final Action mySwitchAction = new SwitchAction();

  public StatementEvaluationDialog(final Project project, TextWithImportsImpl text) {
    super(project, text);
    setTitle("Code Fragment Evaluation");
    myWatchViewPlace.setLayout(new BorderLayout());
    myWatchViewPlace.add(getEvaluationPanel());
    myStatementEditorPlace.setLayout(new BorderLayout());
    myStatementEditorPlace.add(getStatementEditor());

    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel);

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
    PsiElement[] children = ((PsiCodeFragment)psiFile).getChildren();
    int nonWhite = 0;
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      if(!(child instanceof PsiWhiteSpace)) {
        nonWhite++;
        if(nonWhite > 1) {
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
      final TextWithImportsImpl text = (TextWithImportsImpl)getEditor().getText();
      doCancelAction();
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        public void run() {
          EvaluateAction.showEvaluationDialog(getProject(), text, DebuggerSettings.EVALUATE_EXPRESSION);
        }
      });
    }
  }


}
