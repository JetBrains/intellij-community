package com.intellij.refactoring.copy;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class CopyClassDialog extends DialogWrapper{
  private JLabel myInformationLabel = new JLabel("X");
  private JLabel myNameLabel = new JLabel("Name:");
  private EditorTextField myNameField;
  private JLabel myPackageLabel = new JLabel("Destination package:");
  private TextFieldWithBrowseButton myTfPackage;
  private Project myProject;
  private PsiDirectory myTargetDirectory;
  private boolean myDoClone;
  private final PsiDirectory myDefaultTargetDirectory;

  public CopyClassDialog(PsiClass aClass, PsiDirectory defaultTargetDirectory, Project project, boolean doClone) {
    super(project, true);
    init();
    myProject = project;
    myDoClone = doClone;
    myInformationLabel.setText((myDoClone ? "Clone " : "Copy ") + UsageViewUtil.getType(aClass) + " " + UsageViewUtil.getLongName(aClass));
    myNameField.setText(UsageViewUtil.getShortName(aClass));
    myDefaultTargetDirectory = defaultTargetDirectory;
    if (myDefaultTargetDirectory != null) {
      PsiPackage aPackage = myDefaultTargetDirectory.getPackage();
      if (aPackage != null) {
        myTfPackage.setText(aPackage.getQualifiedName());
      }
    }
    if (myDoClone) {
      myTfPackage.setVisible(false);
      myPackageLabel.setVisible(false);
    }
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    return panel;
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    panel.setBorder(IdeBorderFactory.createBorder());

    gbConstraints.insets = new Insets(4,8,4,8);
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 2;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(myInformationLabel, gbConstraints);

    gbConstraints.gridwidth = 1;
    gbConstraints.gridy = 1;
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 0;
    panel.add(myNameLabel, gbConstraints);
    myNameLabel.setLabelFor(myNameField);
    myNameLabel.setDisplayedMnemonic('N');

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    myNameField = new EditorTextField("");
    panel.add(myNameField, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridy = 2;
    gbConstraints.weightx = 0;
    panel.add(myPackageLabel, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    myTfPackage = new TextFieldWithBrowseButton();
    myPackageLabel.setLabelFor(myTfPackage.getTextField());
    myPackageLabel.setDisplayedMnemonic('D');

    myTfPackage.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        PackageChooserDialog chooser=new PackageChooserDialog("Choose Destination Package",myProject);
        chooser.selectPackage(myTfPackage.getText());
        chooser.show();
        PsiPackage aPackage = chooser.getSelectedPackage();
        if (aPackage != null) {
          myTfPackage.setText(aPackage.getQualifiedName());
        }
      }
    });
    panel.add(myTfPackage, gbConstraints);

    return panel;
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  private String getPackageName() {
    String name = myTfPackage.getText();
    return name != null ? name.trim() : "";
  }

  public String getClassName() {
    return myNameField.getText();
  }

  protected void doOKAction(){
    final String packageName = getPackageName();
    final String className = getClassName();

    final String[] errorString = new String[1];
    final PsiManager manager = PsiManager.getInstance(myProject);
    if ("".equals(className)) {
      errorString[0] = "No class name specified";
    }
    else if (!manager.getNameHelper().isIdentifier(className)) {
      errorString[0] = RefactoringMessageUtil.getIncorrectIdentifierMessage(className);
    }
    else if (!myDoClone) {
      try {
        myTargetDirectory = PackageUtil.findOrCreateDirectoryForPackage(myProject, packageName, myDefaultTargetDirectory, true);
        if (myTargetDirectory == null) {
          errorString[0] = ""; // message already reported by PackageUtil
        } else {
          CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  errorString[0] = RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, className);
                }
              });
            }
          }, "Create directory", null);
        }
      }
      catch (IncorrectOperationException e) {
        errorString[0] = e.getMessage();
      }
    }

    if (errorString[0] != null) {
      if (errorString[0].length() > 0) {
        Messages.showMessageDialog(myProject, errorString[0], "Error", Messages.getErrorIcon());
      }
      myNameField.requestFocusInWindow();
      return;
    }
    super.doOKAction();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.COPY_CLASS);
  }
}
