/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 22, 2004
 */
public class ProjectOutputPathsStep extends ModuleWizardStep{
  private JPanel myPanel;
  private NamePathComponent myNamePathComponent;
  private WizardContext myWizardContext;

  public ProjectOutputPathsStep(WizardContext wizardContext) {
    myWizardContext = wizardContext;
    myNamePathComponent = new NamePathComponent("", IdeBundle.message("label.select.compiler.output.path"), IdeBundle.message("title.select.compiler.output.path"), "", false);
    myNamePathComponent.setNameComponentVisible(false);
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());
    myPanel.add(myNamePathComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 6, 0, 6), 0, 0));
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void updateDataModel() {
    myWizardContext.setCompilerOutputDirectory(myNamePathComponent.getPath());
  }

  public void updateStep() {
    if (!myNamePathComponent.isPathChangedByUser()) {
      final String projectFilePath = myWizardContext.getProjectFileDirectory();
      if (projectFilePath != null) {
        @NonNls String path = myWizardContext.getCompilerOutputDirectory();
        if (path == null) {
          path = StringUtil.endsWithChar(projectFilePath, '/') ? projectFilePath + "classes" : projectFilePath + "/classes";
        }
        myNamePathComponent.setPath(path.replace('/', File.separatorChar));
        myNamePathComponent.getPathComponent().selectAll();
      }
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return  myNamePathComponent.getPathComponent();
  }

  public boolean isStepVisible() {
    return myWizardContext.getProjectFileDirectory() != null;
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpId() {
    return null;
  }

  public String getCompileOutputPath() {
    return myNamePathComponent.getPath();
  }
}
