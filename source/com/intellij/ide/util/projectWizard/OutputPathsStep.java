package com.intellij.ide.util.projectWizard;

import com.intellij.ide.util.projectWizard.j2ee.WebModuleBuilder;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import org.jetbrains.annotations.NonNls;

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

  public OutputPathsStep(NameLocationStep nameLocationStep, JavaModuleBuilder descriptor, Icon icon, @NonNls String helpId) {
    myDescriptor = descriptor;
    myIcon = icon;
    myHelpId = helpId;
    myNameLocationStep = nameLocationStep;
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
    myDescriptor.setCompilerOutputPath(myNamePathComponent.getPath());
  }

  public void updateStep() {
    if (!myNamePathComponent.isPathChangedByUser()) {
      final String contentEntryPath = myDescriptor.getContentEntryPath();
      if (contentEntryPath != null) {
        @NonNls String path = null;
        if (myDescriptor instanceof WebModuleBuilder) {
          final String explodedPath = ((WebModuleBuilder)myDescriptor).explodedDirPath;
          if (explodedPath != null) {
            path = explodedPath + "/WEB-INF/classes";
          }
        }
        if (path == null) {
          path = StringUtil.endsWithChar(contentEntryPath, '/') ? contentEntryPath + "classes" : contentEntryPath + "/classes";
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
    return myNameLocationStep.getContentEntryPath() != null;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public String getHelpId() {
    return myHelpId;
  }
}
