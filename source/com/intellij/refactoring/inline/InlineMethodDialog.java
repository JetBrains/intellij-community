
package com.intellij.refactoring.inline;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.RefactoringDialog;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class InlineMethodDialog extends RefactoringDialog implements InlineOptions {
  public static final String REFACTORING_NAME = "Inline Method";
  public static interface Callback {
    void run(InlineMethodDialog dialog);
  }

  private JLabel myMethodNameLabel = new JLabel();

  private final PsiMethod myMethod;
  private final boolean myInvokedOnReference;
  private final Callback myCallback;

  private JRadioButton myRbInlineAll;
  private JRadioButton myRbInlineThisOnly;

  public InlineMethodDialog(Project project, PsiMethod method, boolean invokedOnReference, Callback callback) {
    super(project, true);
    myMethod = method;
    myInvokedOnReference = invokedOnReference;
    myCallback = callback;

    setTitle(REFACTORING_NAME);

    init();

    String methodText = PsiFormatUtil.formatMethod(myMethod,
        PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS, PsiFormatUtil.SHOW_TYPE);
    myMethodNameLabel.setText("Method " + methodText);
  }

  public boolean isInlineThisOnly() {
    return myRbInlineThisOnly.isSelected();
  }

  protected JComponent createNorthPanel() {
    return myMethodNameLabel;
  }

  protected JComponent createCenterPanel() {
    JPanel optionsPanel = new JPanel();
    optionsPanel.setBorder(IdeBorderFactory.createTitledBorder("Inline"));
    optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));

    myRbInlineAll = new JRadioButton("All invocations and remove the method", true);
    myRbInlineAll.setMnemonic('A');
    myRbInlineThisOnly = new JRadioButton("This invocation only and keep the method");
    myRbInlineThisOnly.setMnemonic('t');

    optionsPanel.add(myRbInlineAll);
    optionsPanel.add(myRbInlineThisOnly);
    ButtonGroup bg = new ButtonGroup();
    bg.add(myRbInlineAll);
    bg.add(myRbInlineThisOnly);

    myRbInlineThisOnly.setEnabled(myInvokedOnReference);
    final boolean writable = myMethod.isWritable();
    myRbInlineAll.setEnabled(writable);
    if(myInvokedOnReference) {
      if (writable) {
        final boolean inline_method_this = RefactoringSettings.getInstance().INLINE_METHOD_THIS;
        myRbInlineThisOnly.setSelected(inline_method_this);
        myRbInlineAll.setSelected(!inline_method_this);
      }
      else {
        myRbInlineAll.setSelected(false);
        myRbInlineThisOnly.setSelected(true);
      }
    }
    else {
      myRbInlineAll.setSelected(true);
      myRbInlineThisOnly.setSelected(false);
    }
    getPreviewAction().setEnabled(myRbInlineAll.isSelected());
    myRbInlineAll.addItemListener(
      new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          boolean enabled = myRbInlineAll.isSelected();
          getPreviewAction().setEnabled(enabled);
        }
      }
    );

    return optionsPanel;
  }

  protected void doAction() {
    myCallback.run(this);
    RefactoringSettings settings = RefactoringSettings.getInstance();
    if(myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
      settings.INLINE_METHOD_THIS = isInlineThisOnly();
    }
  }
}