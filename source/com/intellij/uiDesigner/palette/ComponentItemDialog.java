package com.intellij.uiDesigner.palette;

import com.intellij.ide.util.TreeClassChooserDialog;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.PsiClass;
import com.intellij.uiDesigner.core.GridConstraints;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Vladimir Kondratyev
 */
public final class ComponentItemDialog extends DialogWrapper{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.palette.ComponentItemDialog");

  private JPanel myPanel;
  private TextFieldWithBrowseButton myTfClassName;
  private final ComponentItem myItemToBeEdited;
  private JLabel myLblClass;
  private JLabel myLblIcon;
  private JTextField myTfIconPath;
  private JCheckBox myChkHorCanShrink;
  private JCheckBox myChkHorCanGrow;
  private JCheckBox myChkHorWantGrow;
  private JCheckBox myChkVerCanShrink;
  private JCheckBox myChkVerCanGrow;
  private JCheckBox myChkVerWantGrow;

  /**
   * @param itemToBeEdited item to be edited. If user closes dialog by "OK" button then
   */
  public ComponentItemDialog(final Project project, final Component parent, final ComponentItem itemToBeEdited) {
    super(parent, false);
    LOG.assertTrue(itemToBeEdited != null);

    myItemToBeEdited = itemToBeEdited;

    myTfClassName.setText(myItemToBeEdited.getClassName());
    myTfClassName.getButton().setEnabled(!project.isDefault()); // chooser should not work in default project
    myTfClassName.addActionListener(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project).createProjectScopeChooser("Choose Component Class");
          chooser.showDialog();
          final PsiClass result = chooser.getSelectedClass();
          if (result != null) {
            myTfClassName.setText(result.getQualifiedName());
          }
        }
      }
    );

    myTfIconPath.setText(myItemToBeEdited.getIconPath());

    final GridConstraints defaultConstraints = myItemToBeEdited.getDefaultConstraints();

    // Horizontal size policy
    {
      final int hSizePolicy = defaultConstraints.getHSizePolicy();
      myChkHorCanShrink.setSelected((hSizePolicy & GridConstraints.SIZEPOLICY_CAN_SHRINK) != 0);
      myChkHorCanGrow.setSelected((hSizePolicy & GridConstraints.SIZEPOLICY_CAN_GROW) != 0);
      myChkHorWantGrow.setSelected((hSizePolicy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0);
    }

    // Vertical size policy
    {
      final int vSizePolicy = defaultConstraints.getVSizePolicy();
      myChkVerCanShrink.setSelected((vSizePolicy & GridConstraints.SIZEPOLICY_CAN_SHRINK) != 0);
      myChkVerCanGrow.setSelected((vSizePolicy & GridConstraints.SIZEPOLICY_CAN_GROW) != 0);
      myChkVerWantGrow.setSelected((vSizePolicy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0);
    }

    myLblClass.setLabelFor(myTfClassName);
    myLblIcon.setLabelFor(myTfIconPath);

    init();
  }



  protected void doOKAction() {
    // TODO[vova] implement validation
    myItemToBeEdited.setClassName(myTfClassName.getText().trim());
    myItemToBeEdited.setIconPath(myTfIconPath.getText().trim());

    // Horizontal size policy
    {
      final GridConstraints defaultConstraints = myItemToBeEdited.getDefaultConstraints();
      defaultConstraints.setHSizePolicy(
        (myChkHorCanShrink.isSelected() ? GridConstraints.SIZEPOLICY_CAN_SHRINK : 0) |
        (myChkHorCanGrow.isSelected() ? GridConstraints.SIZEPOLICY_CAN_GROW : 0) |
        (myChkHorWantGrow.isSelected() ? GridConstraints.SIZEPOLICY_WANT_GROW : 0)
      );
    }

    // Vertical size policy
    {
      final GridConstraints defaultConstraints = myItemToBeEdited.getDefaultConstraints();
      defaultConstraints.setVSizePolicy(
        (myChkVerCanShrink.isSelected() ? GridConstraints.SIZEPOLICY_CAN_SHRINK : 0) |
        (myChkVerCanGrow.isSelected() ? GridConstraints.SIZEPOLICY_CAN_GROW : 0) |
        (myChkVerWantGrow.isSelected() ? GridConstraints.SIZEPOLICY_WANT_GROW : 0)
      );
    }

    super.doOKAction();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.uiDesigner.palette.ComponentItemDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myTfClassName.getTextField();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
