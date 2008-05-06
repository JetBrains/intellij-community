/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring.replaceMethodWithMethodObject;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

public class ReplaceMethodWithMethodObjectDialog extends RefactoringDialog{
  private final PsiMethod myMethod;
  private JTextField myInnerClassNameField = new JTextField();

  protected ReplaceMethodWithMethodObjectDialog(@NotNull PsiMethod method) {
    super(method.getProject(), false);
    myMethod = method;
    init();
    setTitle(ReplaceMethodWithMethodObjectProcessor.REFACTORING_NAME);
  }

  protected void doAction() {
    final ReplaceMethodWithMethodObjectProcessor processor = new ReplaceMethodWithMethodObjectProcessor(myMethod, myInnerClassNameField.getText());
    invokeRefactoring(processor);
  }

  @Override
  protected boolean areButtonsValid() {
    final String innerClassName = myInnerClassNameField.getText();
    final boolean isIdentifier =
        JavaPsiFacade.getInstance(myMethod.getProject()).getNameHelper().isIdentifier(innerClassName);
    if (!isIdentifier) return false;
    final PsiClass psiClass = myMethod.getContainingClass().findInnerClassByName(innerClassName, true);
    if (psiClass != null) return false;
    return true;
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST,
                                                         GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    panel.add(new JLabel("Class Name:"), gc);
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;

    String innerClassName = StringUtil.capitalize(myMethod.getName());
    int idx = 1;
    while (myMethod.getContainingClass().findInnerClassByName(innerClassName, true) != null) {
      innerClassName += idx++;
    }
    myInnerClassNameField.setText(innerClassName);

    myInnerClassNameField.selectAll();
    panel.add(myInnerClassNameField, gc);
    myInnerClassNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        validateButtons();
      }
    });
    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myInnerClassNameField;
  }
}