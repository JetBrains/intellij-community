package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiVariable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Alexey Kudravtsev
 */
public class SideEffectWarningDialog extends DialogWrapper {
  private final PsiVariable myVariable;
  private final String mySampleExpressionText;
  private final boolean myCanCopeWithSideEffects;
  private AbstractAction myMakeStmtAction;
  private AbstractAction myRemoveAllAction;
  private AbstractAction myCancelAllAction;
  public static final int MAKE_STATEMENT = 1;
  public static final int DELETE_ALL = 2;
  public static final int CANCEL = 0;

  public SideEffectWarningDialog(Project project, boolean canBeParent, PsiVariable variable, String sampleExpressionText, boolean canCopeWithSideEffects) {
    super(project, canBeParent);
    myVariable = variable;
    mySampleExpressionText = sampleExpressionText;
    myCanCopeWithSideEffects = canCopeWithSideEffects;
    setTitle("Side Effects Found");
    init();

  }

  protected Action[] createActions() {
    List<AbstractAction> actions = new ArrayList<AbstractAction>();
    myRemoveAllAction = new AbstractAction() {
      {
        putValue(Action.NAME, "Remove");
        putValue(Action.MNEMONIC_KEY, new Integer('R'));
        putValue(DEFAULT_ACTION, this);
      }

      public void actionPerformed(ActionEvent e) {
        close(DELETE_ALL);
      }

    };
    actions.add(myRemoveAllAction);
    if (myCanCopeWithSideEffects) {
      myMakeStmtAction = new AbstractAction() {
        {
          putValue(Action.NAME, "Transform");
          putValue(Action.MNEMONIC_KEY, new Integer('T'));
        }

        public void actionPerformed(ActionEvent e) {
          close(MAKE_STATEMENT);
        }

      };
      actions.add(myMakeStmtAction);
    }
    myCancelAllAction = new AbstractAction() {
      {
        putValue(Action.NAME, "Cancel");
        putValue(Action.MNEMONIC_KEY, new Integer('C'));
      }

      public void actionPerformed(ActionEvent e) {
        doCancelAction();
      }

    };
    actions.add(myCancelAllAction);
    return actions.toArray(new Action[actions.size()]);
  }

  protected Action getCancelAction() {
    return myCancelAllAction;
  }

  protected Action getOKAction() {
    return myRemoveAllAction;
  }

  public void doCancelAction() {
    close(CANCEL);
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());

    String text =
            "<html>There are possible side effects found in expressions assigned to the variable '" + myVariable.getName()+"'"
            +"<br>"
            +"You can:"
            +"<ul>"
            + "<li><b>Remove</b> variable usages along with all expressions involved" + (myCanCopeWithSideEffects ? ", or" : "") + "</li>"
      ;
    if (myCanCopeWithSideEffects) {
      text+= "<li><b>Transform</b> expressions assigned to variable into the statements on their own."
             +"<br>That is,<br>"
             +"<table border=1><tr><td><code>"+myVariable.getType().getPresentableText()+" "+myVariable.getName()+" = "+mySampleExpressionText+";</code></td></tr></table>"
             +"<br> becomes: <br>"
             +"<table border=1><tr><td><code>"+mySampleExpressionText+";</code></td></tr></table>"
             +"</li>";
    }

    final JLabel label = new JLabel(text);
    label.setIcon(Messages.getWarningIcon());
    panel.add(label, BorderLayout.NORTH);
    return panel;
  }
}
