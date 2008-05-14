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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ReplaceMethodWithMethodObjectDialog extends RefactoringDialog{
  private final PsiMethod myMethod;
  private JTextField myInnerClassNameField;
  private JRadioButton myCreateAnonymousClassRb;
  private JRadioButton myCreateInnerClassRb;
  private JPanel myWholePanel;


  protected ReplaceMethodWithMethodObjectDialog(@NotNull PsiMethod method) {
    super(method.getProject(), false);
    myMethod = method;
    init();
    setTitle(ReplaceMethodWithMethodObjectProcessor.REFACTORING_NAME);
  }

  protected void doAction() {
    final ReplaceMethodWithMethodObjectProcessor processor = new ReplaceMethodWithMethodObjectProcessor(myMethod, myInnerClassNameField.getText(), myCreateInnerClassRb.isSelected());
    invokeRefactoring(processor);
  }

  @Override
  protected boolean areButtonsValid() {
    if (myCreateAnonymousClassRb.isSelected()) return true;
    final String innerClassName = myInnerClassNameField.getText();
    final boolean isIdentifier =
        JavaPsiFacade.getInstance(myMethod.getProject()).getNameHelper().isIdentifier(innerClassName);
    if (!isIdentifier) return false;
    final PsiClass psiClass = myMethod.getContainingClass().findInnerClassByName(innerClassName, true);
    if (psiClass != null) return false;
    return true;
  }

  protected JComponent createCenterPanel() {

    String innerClassName = StringUtil.capitalize(myMethod.getName());
    int idx = 1;
    while (myMethod.getContainingClass().findInnerClassByName(innerClassName, true) != null) {
      innerClassName += idx++;
    }
    myInnerClassNameField.setText(innerClassName);
    myInnerClassNameField.selectAll();
    myInnerClassNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        validateButtons();
      }
    });
    myCreateInnerClassRb.setSelected(true);
    final ActionListener enableDisableListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myInnerClassNameField.setEnabled(myCreateInnerClassRb.isSelected());
      }
    };
    myCreateInnerClassRb.addActionListener(enableDisableListener);
    myCreateAnonymousClassRb.addActionListener(enableDisableListener);
    return myWholePanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myInnerClassNameField;
  }
}