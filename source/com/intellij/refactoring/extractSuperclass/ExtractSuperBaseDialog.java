package com.intellij.refactoring.extractSuperclass;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.refactoring.ui.BaseRefactoringDialog;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author dsl
 */
public abstract class ExtractSuperBaseDialog extends BaseRefactoringDialog {
  protected JRadioButton myRbExtractSuperclass;
  private JRadioButton myRbExtractSubclass;

  public ExtractSuperBaseDialog(Project project, boolean canBeParent) {
    super(project, canBeParent);
  }

  protected JComponent createActionComponent() {
    Box box = Box.createHorizontalBox();
    final String s = StringUtil.decapitalize(getEntityName());
    myRbExtractSuperclass = new JRadioButton("Extract " + s);
    myRbExtractSubclass = new JRadioButton("Rename original class and use " + s + " where possible");
    box.add(myRbExtractSuperclass);
    box.add(myRbExtractSubclass);
    box.add(Box.createHorizontalGlue());
    final ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbExtractSuperclass);
    buttonGroup.add(myRbExtractSubclass);
    myRbExtractSuperclass.setSelected(true);
    myRbExtractSuperclass.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateDialogForExtractSuperclass();
      }
    });

    myRbExtractSubclass.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateDialogForExtractSubclass();
      }
    });
    return box;
  }

  private void updateDialogForExtractSubclass() {
    getClassNameLabel().setText("Rename original class to:");
    getClassNameLabel().setDisplayedMnemonic('R');
    getPreviewAction().setEnabled (true);
  }

  protected abstract JLabel getClassNameLabel();

  protected abstract JLabel getPackageNameLabel();

  protected void updateDialogForExtractSuperclass() {
    getClassNameLabel().setText(StringUtil.capitalize(getEntityName()) + " name:");
    getClassNameLabel().setDisplayedMnemonic('S');
    getPreviewAction().setEnabled (false);
  }

  protected abstract String getEntityName();

  public boolean isExtractSuperclass() {
    return myRbExtractSuperclass.isSelected();
  }
}
