package com.intellij.ide.util;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author dsl
 */
public class DeleteDialog extends DialogWrapper {
  private final PsiElement[] myElements;
  private final Callback myCallback;

  private StateRestoringCheckBox myCbSearchInComments;
  private StateRestoringCheckBox myCbSearchInNonJava;
  private JCheckBox myCbSafeDelete;

  public interface Callback {
    void run(DeleteDialog dialog);
  }

  public DeleteDialog(Project project, PsiElement[] elements, Callback callback) {
    super(project, true);
    myElements = elements;
    myCallback = callback;

    setTitle(SafeDeleteHandler.REFACTORING_NAME);
    init();
  }

  public boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }

  public boolean isSearchInNonJava() {
    return myCbSearchInNonJava.isSelected();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()/*, getHelpAction()*/};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.SAFE_DELETE);
  }

  protected JComponent createNorthPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gbc = new GridBagConstraints();

    final String warningMessage = DeleteUtil.generateWarningMessage(IdeBundle.message("prompt.delete.elements"), myElements);

    gbc.insets = new Insets(4, 8, 4, 8);
    gbc.weighty = 1;
    gbc.weightx = 1;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.WEST;
    panel.add(new JLabel(warningMessage), gbc);

    gbc.gridy++;
    gbc.gridx = 0;
    gbc.weightx = 0.0;
    gbc.gridwidth = 1;
    gbc.insets = new Insets(4, 8, 0, 8);
    myCbSafeDelete = new JCheckBox(IdeBundle.message("checkbox.safe.delete.with.usage.search"));
    panel.add(myCbSafeDelete, gbc);

    gbc.gridy++;
    gbc.gridx = 0;
    gbc.weightx = 0.0;
    gbc.gridwidth = 1;
    gbc.insets = new Insets(0, 8, 4, 8);
    myCbSearchInComments = new StateRestoringCheckBox(IdeBundle.message("checkbox.search.in.comments.and.strings"));
    panel.add(myCbSearchInComments, gbc);

    gbc.gridx++;
    myCbSearchInNonJava = new StateRestoringCheckBox(IdeBundle.message("checkbox.search.in.non.java.files"));
    panel.add(myCbSearchInNonJava, gbc);

    final JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
    myCbSafeDelete.setSelected(refactoringSettings.SAFE_DELETE_WHEN_DELETE);
    myCbSearchInComments.setSelected(refactoringSettings.SAFE_DELETE_SEARCH_IN_COMMENTS);
    myCbSearchInNonJava.setSelected(refactoringSettings.SAFE_DELETE_SEARCH_IN_NON_JAVA);

    myCbSafeDelete.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateControls();
      }
    });
    updateControls();
    return panel;
  }

  private void updateControls() {
    if(myCbSafeDelete.isSelected()) {
      myCbSearchInComments.makeSelectable();
      myCbSearchInNonJava.makeSelectable();
    }
    else {
      myCbSearchInComments.makeUnselectable(false);
      myCbSearchInNonJava.makeUnselectable(false);
    }
  }

  protected JComponent createCenterPanel() {
    return null;
  }


  protected void doOKAction() {
    final JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
    refactoringSettings.SAFE_DELETE_WHEN_DELETE = myCbSafeDelete.isSelected();
    if (myCbSafeDelete.isSelected()) {
      if (myCallback != null) {
        myCallback.run(this);
      } else {
        super.doOKAction();
      }
      refactoringSettings.SAFE_DELETE_SEARCH_IN_COMMENTS = isSearchInComments();
      refactoringSettings.SAFE_DELETE_SEARCH_IN_NON_JAVA = isSearchInNonJava();
    }
    else {
      super.doOKAction();
    }
  }
}
