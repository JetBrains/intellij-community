package com.intellij.codeInsight.actions;

import com.intellij.openapi.help.HelpManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.help.HelpManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class LayoutCodeDialog extends DialogWrapper {

  private final PsiFile myFile;
  private final PsiDirectory myDirectory;
  private final Boolean myTextSelected;

  private JRadioButton myRbFile;
  private JRadioButton myRbSelectedText;
  private JRadioButton myRbDirectory;
  private JCheckBox myCbIncludeSubdirs;
  private JCheckBox myCbOptimizeImports;
  private static final String OPTIMIZE_IMPORTS_KEY = "LayoutCode.optimizeImports";
  private final String myHelpId;

  public LayoutCodeDialog(Project project,
                          String title,
                          PsiFile file,
                          PsiDirectory directory,
                          Boolean isTextSelected,
                          final String helpId) {
    super(project, true);
    myFile = file;
    myDirectory = directory;
    myTextSelected = isTextSelected;

    setOKButtonText("Run");
    setTitle(title);
    init();
    myHelpId = helpId;
  }

  protected void init() {
    super.init();

    if (myTextSelected == Boolean.TRUE) {
      myRbSelectedText.setSelected(true);
    }
    else {
      if (myFile != null) {
        myRbFile.setSelected(true);
      }
      else {
        myRbDirectory.setSelected(true);
      }
    }

    myCbIncludeSubdirs.setSelected(true);
    myCbOptimizeImports.setSelected(isOptmizeImportsOptionOn());

    ItemListener listener = new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateState();
      }
    };
    myRbFile.addItemListener(listener);
    myRbSelectedText.addItemListener(listener);
    myRbDirectory.addItemListener(listener);
    myCbIncludeSubdirs.addItemListener(listener);

    updateState();
  }

  private boolean isOptmizeImportsOptionOn() {
    return "true".equals(PropertiesComponent.getInstance().getValue(OPTIMIZE_IMPORTS_KEY));
  }

  private void setOptimizeImportsOption(boolean state) {
    PropertiesComponent.getInstance().setValue(OPTIMIZE_IMPORTS_KEY, state ? "true" : "false");
  }

  private void updateState() {
    myCbIncludeSubdirs.setEnabled(myRbDirectory.isSelected());
    myCbOptimizeImports.setEnabled(
      !myRbSelectedText.isSelected() && !(myFile != null && !(myFile instanceof PsiJavaFile) && myRbFile.isSelected()));
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 0));
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridy = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 3;
    gbConstraints.gridheight = 1;
    gbConstraints.weightx = 1;

    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.insets = new Insets(0, 0, 0, 0);

    myRbFile =
    new JRadioButton("File" + (myFile != null ? " '" + myFile.getVirtualFile().getPresentableUrl() + "'" : ""));
    myRbFile.setMnemonic('F');
    panel.add(myRbFile, gbConstraints);

    myRbSelectedText = new JRadioButton("Selected text");
    myRbSelectedText.setMnemonic('S');
    if (myTextSelected != null) {
      gbConstraints.gridy++;
      gbConstraints.insets = new Insets(0, 0, 0, 0);
      panel.add(myRbSelectedText, gbConstraints);
    }

    myRbDirectory = new JRadioButton("All files in directory " + myDirectory.getVirtualFile().getPresentableUrl());
    myRbDirectory.setMnemonic('A');
    gbConstraints.gridy++;
    gbConstraints.insets = new Insets(0, 0, 0, 0);
    panel.add(myRbDirectory, gbConstraints);

    myCbIncludeSubdirs = new JCheckBox("Include subdirectories");
    myCbIncludeSubdirs.setMnemonic('I');
    if (myDirectory.getSubdirectories().length > 0) {
      gbConstraints.gridy++;
      gbConstraints.insets = new Insets(0, 20, 0, 0);
      panel.add(myCbIncludeSubdirs, gbConstraints);
    }

    myCbOptimizeImports = new JCheckBox("Optimize imports");
    myCbOptimizeImports.setMnemonic('O');
    if (myTextSelected != null) {
      gbConstraints.gridy++;
      gbConstraints.insets = new Insets(0, 0, 0, 0);
      panel.add(myCbOptimizeImports, gbConstraints);
    }

    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbFile);
    buttonGroup.add(myRbSelectedText);
    buttonGroup.add(myRbDirectory);

    myRbFile.setEnabled(myFile != null);
    myRbSelectedText.setEnabled(myTextSelected == Boolean.TRUE);

    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridy = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridheight = 1;
    gbConstraints.weightx = 1;
    gbConstraints.insets = new Insets(0, 4, 0, 0);
    gbConstraints.fill = GridBagConstraints.BOTH;

    return panel;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(),getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpId);
  }

  public boolean isProcessSelectedText() {
    return myRbSelectedText.isSelected();
  }

  public boolean isProcessDirectory() {
    return myRbDirectory.isSelected();
  }

  public boolean isIncludeSubdirectories() {
    return myCbIncludeSubdirs.isSelected();
  }

  public boolean isOptimizeImports() {
    return myCbOptimizeImports.isSelected();
  }

  protected void doOKAction() {
    super.doOKAction();
    setOptimizeImportsOption(isOptimizeImports());
  }
}