package com.intellij.refactoring.inline;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public abstract class InlineOptionsDialog extends RefactoringDialog implements InlineOptions {
  protected JRadioButton myRbInlineAll;
  protected JRadioButton myRbInlineThisOnly;
  protected boolean myInvokedOnReference;
  protected PsiElement myElement;
  private JLabel myNameLabel = new JLabel();
  
  protected InlineOptionsDialog(Project project, boolean canBeParent, PsiElement element) {
    super(project, canBeParent);
    myElement = element;
  }

  protected JComponent createNorthPanel() {
    myNameLabel.setText(getNameLabelText());
    return myNameLabel;
  }

  public boolean isInlineThisOnly() {
    return myRbInlineThisOnly.isSelected();
  }

  protected JComponent createCenterPanel() {
    JPanel optionsPanel = new JPanel();
    optionsPanel.setBorder(IdeBorderFactory.createTitledBorder(getBorderTitle()));
    optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));

    myRbInlineAll = new JRadioButton();
    myRbInlineAll.setText(getInlineAllText());
    myRbInlineAll.setSelected(true);
    myRbInlineThisOnly = new JRadioButton();
    myRbInlineThisOnly.setText(getInlineThisText());

    optionsPanel.add(myRbInlineAll);
    optionsPanel.add(myRbInlineThisOnly);
    ButtonGroup bg = new ButtonGroup();
    bg.add(myRbInlineAll);
    bg.add(myRbInlineThisOnly);

    myRbInlineThisOnly.setEnabled(myInvokedOnReference);
    final boolean writable = myElement.isWritable();
    myRbInlineAll.setEnabled(writable);
    if(myInvokedOnReference) {
      if (canInlineThisOnly()) {
        myRbInlineAll.setSelected(false);
        myRbInlineAll.setEnabled(false);
        myRbInlineThisOnly.setSelected(true);
      } else {
        if (writable) {
          final boolean inlineThis = isInlineThis();
          myRbInlineThisOnly.setSelected(inlineThis);
          myRbInlineAll.setSelected(!inlineThis);
        }
        else {
          myRbInlineAll.setSelected(false);
          myRbInlineThisOnly.setSelected(true);
        }
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

  protected abstract String getNameLabelText();
  protected abstract String getBorderTitle();
  protected abstract String getInlineAllText();
  protected abstract String getInlineThisText();
  protected abstract boolean isInlineThis();
  protected boolean canInlineThisOnly() {
    return false;
  }
}
