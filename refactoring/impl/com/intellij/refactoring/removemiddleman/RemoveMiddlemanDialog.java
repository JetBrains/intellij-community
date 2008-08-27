package com.intellij.refactoring.removemiddleman;

import com.intellij.openapi.help.HelpManager;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactorJHelpID;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.classMembers.MemberInfo;

import javax.swing.*;
import java.awt.*;

@SuppressWarnings({"OverridableMethodCallInConstructor"})
public class RemoveMiddlemanDialog extends RefactoringDialog {

  private JTextField fieldNameLabel;

  private final MemberInfo[] delegateMethods;

  private final PsiField myField;


  RemoveMiddlemanDialog(PsiField field, MemberInfo[] delegateMethods) {
    super(field.getProject(), true);
    myField = field;
    this.delegateMethods = delegateMethods;
    fieldNameLabel = new JTextField();
    fieldNameLabel.setText(
      PsiFormatUtil.formatVariable(myField, PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_NAME, PsiSubstitutor.EMPTY));
    setTitle(RefactorJBundle.message("remove.middleman.title"));
    init();
  }

  protected String getDimensionServiceKey() {
    return "RefactorJ.RemoveMiddleman";
  }


  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
    panel.add(new MemberSelectionPanel("Methods to inline", delegateMethods, "Delete"), BorderLayout.CENTER);
    return panel;
  }

  protected JComponent createNorthPanel() {
    fieldNameLabel.setEditable(false);
    final JPanel sourceClassPanel = new JPanel(new BorderLayout());
    sourceClassPanel.add(new JLabel("Delegating field"), BorderLayout.NORTH);
    sourceClassPanel.add(fieldNameLabel, BorderLayout.CENTER);
    return sourceClassPanel;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(RefactorJHelpID.RemoveMiddleman);
  }

  protected void doAction() {
    invokeRefactoring(new RemoveMiddlemanProcessor(myField, delegateMethods));
  }

  protected boolean areButtonsValid() {
    return true;
  }
}