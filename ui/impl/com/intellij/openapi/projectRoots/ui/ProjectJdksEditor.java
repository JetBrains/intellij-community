package com.intellij.openapi.projectRoots.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ui.configuration.projectRoot.JdksConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectRootConfigurable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.awt.*;

/**
 * @author MYakovlev
 */
public class ProjectJdksEditor extends DialogWrapper{
  private ProjectRootConfigurable myConfigurable;
  private ProjectJdk myProjectJdk;


  public ProjectJdksEditor(ProjectJdk jdk, Project project, Component parent) {
    super(parent, true);
    myConfigurable = ProjectRootConfigurable.getInstance(project);
    myConfigurable.selectNodeInTree(jdk != null ? jdk.getName() : JdksConfigurable.JDKS);
    setTitle(ProjectBundle.message("sdk.configure.title"));
    init();
  }

  public ProjectJdksEditor(ProjectJdk jdk, Component parent){
    this(jdk, (Project)DataManager.getInstance().getDataContext().getData(DataConstants.PROJECT), parent);
  }

  protected JComponent createCenterPanel(){
    JComponent component = myConfigurable.createComponent();
    myConfigurable.reset();
    component.setPreferredSize(new Dimension(600, 300));
    return component;
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