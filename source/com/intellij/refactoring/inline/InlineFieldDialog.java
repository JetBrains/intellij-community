
package com.intellij.refactoring.inline;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class InlineFieldDialog extends RefactoringDialog implements InlineOptions {
  public static final String REFACTORING_NAME = "Inline Field";
  private PsiReferenceExpression myReferenceExpression;

  private JLabel myFieldNameLabel = new JLabel();

  private final PsiField myField;
  private final boolean myInvokedOnReference;

  private JRadioButton myRbInlineAll;
  private JRadioButton myRbInlineThisOnly;

  public InlineFieldDialog(Project project, PsiField field, PsiReferenceExpression ref) {
    super(project, true);
    myField = field;
    myReferenceExpression = ref;
    myInvokedOnReference = myReferenceExpression != null;

    setTitle(REFACTORING_NAME);

    init();

    String fieldText = PsiFormatUtil.formatVariable(myField,
                                                    PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE ,PsiSubstitutor.EMPTY);
    myFieldNameLabel.setText("Field " + fieldText);
  }

  public boolean isInlineThisOnly() {
    return myRbInlineThisOnly.isSelected();
  }

  protected JComponent createNorthPanel() {
    return myFieldNameLabel;
  }

  protected JComponent createCenterPanel() {
    JPanel optionsPanel = new JPanel();
    optionsPanel.setBorder(IdeBorderFactory.createTitledBorder("Inline"));
    optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));

    myRbInlineAll = new JRadioButton("All references and remove the field", true);
    myRbInlineAll.setMnemonic('A');
    myRbInlineThisOnly = new JRadioButton("This reference only and keep the field");
    myRbInlineThisOnly.setMnemonic('t');

    optionsPanel.add(myRbInlineAll);
    optionsPanel.add(myRbInlineThisOnly);
    ButtonGroup bg = new ButtonGroup();
    bg.add(myRbInlineAll);
    bg.add(myRbInlineThisOnly);

    myRbInlineThisOnly.setEnabled(myInvokedOnReference);
    final boolean writable = myField.isWritable();
    myRbInlineAll.setEnabled(writable);
    if(myInvokedOnReference) {
      if (writable) {
        final boolean inlineThis = RefactoringSettings.getInstance().INLINE_FIELD_THIS;
        myRbInlineThisOnly.setSelected(inlineThis);
        myRbInlineAll.setSelected(!inlineThis);
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
    invokeRefactoring(new InlineConstantFieldProcessor(myField, getProject(), myReferenceExpression, isInlineThisOnly()));
    RefactoringSettings settings = RefactoringSettings.getInstance();
    if(myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
      settings.INLINE_FIELD_THIS = isInlineThisOnly();
    }
  }

}