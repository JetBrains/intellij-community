package com.intellij.openapi.projectRoots.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ui.configuration.ProjectJdksConfigurable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.awt.*;

/**
 * @author MYakovlev
 */
public class ProjectJdksEditor extends DialogWrapper {
  private ProjectJdksConfigurable myConfigurable;
  private ProjectJdk myProjectJdk;


  public ProjectJdksEditor(final ProjectJdk jdk, Project project, Component parent) {
    super(parent, true);
    myConfigurable = new ProjectJdksConfigurable(project);
    SwingUtilities.invokeLater(new Runnable(){
      public void run() {
        myConfigurable.selectNodeInTree(jdk != null ? jdk.getName() : null);
      }
    });
    setTitle(ProjectBundle.message("sdk.configure.title"));
    init();
  }

  public ProjectJdksEditor(ProjectJdk jdk, Component parent){
    this(jdk, (Project)DataManager.getInstance().getDataContext().getData(DataConstants.PROJECT), parent);
  }

  protected JComponent createCenterPanel(){
    myConfigurable.reset();
    return myConfigurable.createComponent();
  }

  protected void doOKAction(){
    try{
      myProjectJdk = myConfigurable.getSelectedJdk(); //before dispose
      myConfigurable.apply();
      super.doOKAction();
    }
    catch (ConfigurationException e){
      Messages.showMessageDialog(getContentPane(), e.getMessage(),
                                 ProjectBundle.message("sdk.configure.save.settings.error"), Messages.getErrorIcon());
    }
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.openapi.projectRoots.ui.ProjectJdksEditor";
  }

  public ProjectJdk getSelectedJdk(){
    return myProjectJdk;
  }

}