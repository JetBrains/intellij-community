package com.intellij.refactoring.typeCook;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.help.HelpManager;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.RefactoringDialog;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 30.07.2003
 * Time: 21:36:29
 * To change this template use Options | File Templates.
 */
public class TypeCookDialog extends RefactoringDialog {

  public static final String REFACTORING_NAME = "Generify";

  public static interface Callback {
    void run(TypeCookDialog dialog);
  }

  private final Callback myCallback;
  private JLabel myClassNameLabel = new JLabel();
  private JCheckBox myCbDropCasts = new JCheckBox("Drop obsolete casts");
  private JCheckBox myCbPreserveRawArrays = new JCheckBox("Preserve raw arrays");
  private JCheckBox myCbLeaveObjectParameterizedTypesRaw = new JCheckBox("Leave Object-parameterized types raw");

  public TypeCookDialog(Project project, PsiElement[] elements, Callback callback) {
    super(project, true);

    myCallback = callback;

    setTitle(REFACTORING_NAME);

    init();

    StringBuffer name = new StringBuffer("<html>");

    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];

      if (element instanceof PsiClass) {
        name.append("Class " + ((PsiClass)element).getQualifiedName());
      }
      else if (element instanceof PsiFile) {
        name.append("File " + ((PsiFile)element).getName());
      }
      else if (element instanceof PsiDirectory) {
        name.append("Directory " + ((PsiDirectory)element).getName());
      }
      else if (element instanceof PsiPackage) {
        name.append("Package " + ((PsiPackage)element).getQualifiedName());
      }

      if (i < elements.length - 1) {
        name.append("<br>");
      }
    }

    name.append("</html>");

    myClassNameLabel.setText(name.toString());
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.TYPE_COOK);
  }

  protected JComponent createNorthPanel() {
    JPanel optionsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    optionsPanel.setBorder(IdeBorderFactory.createBorder());

    if (myCbDropCasts.isEnabled()) {
      myCbDropCasts.setSelected(RefactoringSettings.getInstance().TYPE_COOK_DROP_CASTS);
    }

    if (myCbPreserveRawArrays.isEnabled()) {
      myCbPreserveRawArrays.setSelected(RefactoringSettings.getInstance().TYPE_COOK_PRESERVE_RAW_ARRAYS);
    }

    if (myCbLeaveObjectParameterizedTypesRaw.isEnabled()) {
      myCbLeaveObjectParameterizedTypesRaw.setSelected(
        RefactoringSettings.getInstance().TYPE_COOK_LEAVE_OBJECT_PARAMETERIZED_TYPES_RAW);
    }

    myCbDropCasts.setMnemonic('D');
    myCbPreserveRawArrays.setMnemonic('P');
    myCbLeaveObjectParameterizedTypesRaw.setMnemonic('L');

    gbConstraints.insets = new Insets(4, 8, 4, 8);

    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    optionsPanel.add(myClassNameLabel, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    optionsPanel.add(myCbDropCasts, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    optionsPanel.add(myCbPreserveRawArrays, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 2;
    optionsPanel.add(myCbLeaveObjectParameterizedTypesRaw, gbConstraints);

    return optionsPanel;
  }

  protected void doAction() {
    myCallback.run(this);

    RefactoringSettings settings = RefactoringSettings.getInstance();

    settings.TYPE_COOK_DROP_CASTS = myCbDropCasts.isSelected();
    settings.TYPE_COOK_PRESERVE_RAW_ARRAYS = myCbPreserveRawArrays.isSelected();
    settings.TYPE_COOK_LEAVE_OBJECT_PARAMETERIZED_TYPES_RAW = myCbLeaveObjectParameterizedTypesRaw.isSelected();
  }

  public Settings getSettings() {
    final boolean dropCasts = myCbDropCasts.isSelected();
    final boolean preserveRawArrays = true; //myCbPreserveRawArrays.isSelected();
    final boolean leaveObjectParameterizedTypesRaw = myCbLeaveObjectParameterizedTypesRaw.isSelected();

    return new Settings() {
      public boolean dropObsoleteCasts() {
        return dropCasts;
      }

      public boolean preserveRawArrays() {
        return preserveRawArrays;
      }

      public boolean leaveObjectParameterizedTypesRaw() {
        return leaveObjectParameterizedTypesRaw;
      }
    };
  }
}
