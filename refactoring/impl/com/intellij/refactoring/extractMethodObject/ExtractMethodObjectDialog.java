/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring.extractMethodObject;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.extractMethod.ExtractMethodDialog;

import javax.swing.*;
import java.awt.*;

public class ExtractMethodObjectDialog extends ExtractMethodDialog{
  private JRadioButton myCreateAnonymousClassRb = new JRadioButton("Create anonymous class");
  private JRadioButton myCreateInnerClassRb = new JRadioButton("Create inner class");


  public ExtractMethodObjectDialog(Project project, PsiClass targetClass, final PsiVariable[] inputVariables, PsiType returnType,
                                             PsiTypeParameterList typeParameterList, PsiType[] exceptions, boolean isStatic,
                                             boolean canBeStatic,
                                             final boolean canBeChainedConstructor, String initialMethodName, String title,
                                             String helpId,
                                             final PsiElement[] elementsToExtract) {
    super(project, targetClass, inputVariables, returnType, typeParameterList, exceptions, isStatic, canBeStatic, canBeChainedConstructor,
          initialMethodName, title, helpId, elementsToExtract);
    init();
    setTitle(ExtractMethodObjectProcessor.REFACTORING_NAME);
  }


  @Override
  protected JComponent createNorthPanel() {
    final JComponent methodPanel = super.createNorthPanel();
    final JPanel panel = new JPanel(new GridBagLayout());

    final ButtonGroup bg = new ButtonGroup();
    bg.add(myCreateAnonymousClassRb);
    bg.add(myCreateInnerClassRb);
    myCreateInnerClassRb.setSelected(true);

    GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0,0);
    panel.add(myCreateInnerClassRb, gc);
    gc.gridx = 1;
    panel.add(myCreateAnonymousClassRb, gc);
    gc.insets.top = 20;
    gc.gridx = 0;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(methodPanel, gc);
    /*final ActionListener listener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        methodPanel.setBorder(BorderFactory.createTitledBorder(myCreateInnerClassRb.isSelected() ? "Class" : "Method"));
        myCbMakeVarargs.setText(myCreateAnonymousClassRb.isSelected() ? "Declare method varargs" : "Constructor varargs");
      }
    };
    myCreateAnonymousClassRb.addActionListener(listener);
    myCreateInnerClassRb.addActionListener(listener);*/

    return panel;
  }

  public boolean createInnerClass() {
    return myCreateInnerClassRb.isSelected();
  }
}