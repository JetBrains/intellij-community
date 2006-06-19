package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 22, 2004
 */
public class OutputPathsStep extends ModuleWizardStep{
  private final JavaModuleBuilder myDescriptor;
  private final Icon myIcon;
  private final String myHelpId;
  private final NameLocationStep myNameLocationStep;
  private JPanel myPanel;
  private NamePathComponent myNamePathComponent;
  private JRadioButton myRbInheritProjectOutput = new JRadioButton(ProjectBundle.message("project.inherit.compile.output.path"));
  private JRadioButton myRbPerModuleOutput = new JRadioButton(ProjectBundle.message("project.module.compile.output.path"));

  public OutputPathsStep(NameLocationStep nameLocationStep, JavaModuleBuilder descriptor, Icon icon, @NonNls String helpId) {
    myDescriptor = descriptor;
    myIcon = icon;
    myHelpId = helpId;
    myNameLocationStep = nameLocationStep;
    myNamePathComponent = new NamePathComponent("", IdeBundle.message("label.select.compiler.output.path"), IdeBundle.message("title.select.compiler.output.path"), "", false);
    myNamePathComponent.setNameComponentVisible(false);
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());    
    myPanel.add(myRbInheritProjectOutput, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 6, 0, 6), 0, 0));
    myPanel.add(myNamePathComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 10, 0, 6), 0, 0));
    myPanel.add(myRbPerModuleOutput, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 6, 0, 6), 0, 0));

    final ButtonGroup group = new ButtonGroup();
    group.add(myRbInheritProjectOutput);
    group.add(myRbPerModuleOutput);
    final ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        UIUtil.setEnabled(myNamePathComponent, !myRbInheritProjectOutput.isSelected(), true);
      }
    };
    myRbInheritProjectOutput.addActionListener(listener);
    myRbPerModuleOutput.addActionListener(listener);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void updateDataModel() {
    if (myRbInheritProjectOutput.isSelected()){
      myDescriptor.setCompilerOutputPath(null);
    } else {
      myDescriptor.setCompilerOutputPath(myNamePathComponent.getPath());
    }
  }

  public void updateStep() {
    if (!myNamePathComponent.isPathChangedByUser()) {
      final String contentEntryPath = myDescriptor.getContentEntryPath();
      if (contentEntryPath != null) {
        @NonNls String path = myDescriptor.getPathForOutputPathStep();
        if (path == null) {
          path = StringUtil.endsWithChar(contentEntryPath, '/') ? contentEntryPath + "classes" : contentEntryPath + "/classes";
        }
        myNamePathComponent.setPath(path.replace('/', File.separatorChar));
        myNamePathComponent.getPathComponent().selectAll();
      }
    }
    boolean inheritCompileOutput = myDescriptor.getPathForOutputPathStep() == null;
    myRbInheritProjectOutput.setSelected(inheritCompileOutput);
    myRbPerModuleOutput.setSelected(!inheritCompileOutput);
    UIUtil.setEnabled(myNamePathComponent, !inheritCompileOutput, true);
  }

  public JComponent getPreferredFocusedComponent() {
    return  myNamePathComponent.getPathComponent();
  }

  public boolean isStepVisible() {
    return myNameLocationStep.getContentEntryPath() != null;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public String getHelpId() {
    return myHelpId;
  }
}
