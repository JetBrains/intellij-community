package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.help.HelpManager;
import com.intellij.ide.util.DeleteUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.util.RefactoringUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author dsl
 */
public class SafeDeleteDialog extends DialogWrapper {
  private final PsiElement[] myElements;
  private final Callback myCallback;

  private JCheckBox myCbSearchInComments;
  private JCheckBox myCbSearchTextOccurences;

  public interface Callback {
    void run(SafeDeleteDialog dialog);
  }

  public SafeDeleteDialog(Project project, PsiElement[] elements, Callback callback) {
    super(project, true);
    myElements = elements;
    myCallback = callback;

    setTitle(SafeDeleteHandler.REFACTORING_NAME);
    init();
  }

  public boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }

  public boolean isSearchForTextOccurences() {
    if (myCbSearchTextOccurences != null) {
      return myCbSearchTextOccurences.isSelected();
    }
    return false;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.SAFE_DELETE);
  }

  protected JComponent createNorthPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gbc = new GridBagConstraints();

    final String warningMessage = DeleteUtil.generateWarningMessage("Search for usages and delete", myElements);

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
    myCbSearchInComments = new JCheckBox("Search in comments and strings");
    myCbSearchInComments.setMnemonic('S');
    panel.add(myCbSearchInComments, gbc);

    if (needSearchForTextOccurences()) {
      gbc.gridx++;
      myCbSearchTextOccurences = new JCheckBox("Search for text occurences");
      myCbSearchTextOccurences.setMnemonic('t');
      panel.add(myCbSearchTextOccurences, gbc);
    }

    final RefactoringSettings refactoringSettings = RefactoringSettings.getInstance();
    myCbSearchInComments.setSelected(refactoringSettings.SAFE_DELETE_SEARCH_IN_COMMENTS);
    if (myCbSearchTextOccurences != null) {
      myCbSearchTextOccurences.setSelected(refactoringSettings.SAFE_DELETE_SEARCH_IN_NON_JAVA);
    }
    return panel;
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  private boolean needSearchForTextOccurences() {
    for (PsiElement element : myElements) {
      if (RefactoringUtil.isSearchTextOccurencesEnabled(element)) {
        return true;
      }
    }
    return false;
  }


  protected void doOKAction() {
    if (myCallback != null) {
      myCallback.run(this);
    } else {
      super.doOKAction();
    }
    final RefactoringSettings refactoringSettings = RefactoringSettings.getInstance();
    refactoringSettings.SAFE_DELETE_SEARCH_IN_COMMENTS = isSearchInComments();
    if (myCbSearchTextOccurences != null) {
      refactoringSettings.SAFE_DELETE_SEARCH_IN_NON_JAVA = isSearchForTextOccurences();
    }
  }
}
