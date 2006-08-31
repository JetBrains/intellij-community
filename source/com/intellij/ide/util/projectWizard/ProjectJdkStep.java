/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 16-Aug-2006
 * Time: 18:01:13
 */
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.ProjectJdksConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 21, 2004
 */
public class ProjectJdkStep extends ModuleWizardStep {
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");
  private WizardContext myContext;
  private boolean myInitialized = false;

  private ProjectJdksConfigurable myProjectJdksConfigurable;

  private JComponent myJDKsComponent;

  public ProjectJdkStep(final WizardContext context) {
    myContext = context;
    myProjectJdksConfigurable = new ProjectJdksConfigurable(ProjectManager.getInstance().getDefaultProject());
    myProjectJdksConfigurable.reset();
    myJDKsComponent = myProjectJdksConfigurable.createComponent();
  }

  public JComponent getPreferredFocusedComponent() {
    return myJDKsComponent;
  }

  public String getHelpId() {
    return "project.new.page2";
  }

  public JComponent getComponent() {
    final JLabel label = new JLabel(IdeBundle.message("prompt.please.select.project.jdk"));
    label.setUI(new MultiLineLabelUI());
    final JPanel panel = new JPanel(new GridBagLayout()){
      public Dimension getPreferredSize() {
        return new Dimension(-1, 200);
      }
    };
    panel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0,GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0),0,0));
    myJDKsComponent.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
    panel.add(myJDKsComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0,0,0,0), 0, 0));
    return panel;
  }

  public void updateDataModel() {
    myContext.setProjectJdk(getJdk());
  }


  public void updateStep() {
    if (!myInitialized) { //lazy default project initialization
      ProjectJdk defaultJdk = getDefaultJdk();
      if (defaultJdk != null) {
        myProjectJdksConfigurable.selectJdk(defaultJdk);
      }
      myInitialized = true;
    }
  }

  public ProjectJdk getJdk() {
    return myProjectJdksConfigurable.getSelectedJdk();
  }

  public Icon getIcon() {
    return NEW_PROJECT_ICON;
  }

  private static ProjectJdk getDefaultJdk() {
    Project defaultProject = ProjectManagerEx.getInstanceEx().getDefaultProject();
    return ProjectRootManagerEx.getInstanceEx(defaultProject).getProjectJdk();
  }


  public boolean validate() {
    final ProjectJdk jdk = myProjectJdksConfigurable.getSelectedJdk();
    if (jdk == null) {
      int result = Messages.showOkCancelDialog(IdeBundle.message("prompt.confirm.project.no.jdk"),
                                               IdeBundle.message("title.no.jdk.specified"), Messages.getWarningIcon());
      if (result != 0) {
        return false;
      }
    }
    try {
      myProjectJdksConfigurable.apply();
    }
    catch (ConfigurationException e) {
      Messages.showErrorDialog(myJDKsComponent, e.getMessage(), e.getTitle());
      return false;
    }
    return true;
  }
}