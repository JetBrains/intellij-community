package com.intellij.refactoring.inline;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class InlineParameterDialog extends RefactoringMessageDialog {
  private JCheckBox myCreateLocalCheckbox;
  private static Boolean ourCreateLocalInTests = null;

  public InlineParameterDialog(String title, String message, String helpTopic, @NonNls String iconId, boolean showCancelButton, Project project) {
    super(title, message, helpTopic, iconId, showCancelButton, project);
  }

  protected JComponent createNorthPanel() {
    JComponent superPanel = super.createNorthPanel();
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(superPanel, BorderLayout.CENTER);
    myCreateLocalCheckbox = new JCheckBox("&Replace with local variable");
    panel.add(myCreateLocalCheckbox, BorderLayout.SOUTH);
    return panel;
  }

  public boolean isCreateLocal() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      return myCreateLocalCheckbox.isSelected();
    }
    if (ourCreateLocalInTests == null) {
      throw new RuntimeException("No behavior in tests specified");
    }
    boolean result = ourCreateLocalInTests.booleanValue();
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourCreateLocalInTests = null;
    return result;
  }

  public boolean showDialog() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      show();
      return isOK();
    }
    return true;
  }

  @TestOnly
  public static void setCreateLocalInTests(boolean createLocal) {
    ourCreateLocalInTests = createLocal;
  }
}
