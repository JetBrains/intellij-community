package com.intellij.openapi.projectRoots.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.ProjectBundle;

import javax.swing.*;
import java.awt.*;

/**
 * @author MYakovlev
 */
public class ProjectJdksEditor extends DialogWrapper{
  private JdkTableConfigurable myJdkTableConfigurable;

  public ProjectJdksEditor(ProjectJdk jdk, Component parent){
    super(parent, true);
    myJdkTableConfigurable = new JdkTableConfigurable(jdk);
    setTitle(ProjectBundle.message("sdk.configure.title"));
    init();
  }

  protected JComponent createCenterPanel(){
    JComponent component = myJdkTableConfigurable.createComponent();
    component.setPreferredSize(new Dimension(600, 300));
    return component;
  }

  protected void doOKAction(){
    try{
      myJdkTableConfigurable.apply();
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
    return myJdkTableConfigurable.getSelectedJdk();
  }

}