package com.intellij.openapi.projectRoots.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.ProjectJdksConfigurable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;

import javax.swing.*;
import java.awt.*;

/**
 * @author MYakovlev
 */
public class ProjectJdksEditor extends DialogWrapper {
  private ProjectJdksConfigurable myConfigurable;
  private Sdk myProjectJdk;


  public ProjectJdksEditor(final Sdk jdk, Project project, Component parent) {
    super(parent, true);
    myConfigurable = new ProjectJdksConfigurable(project);
    SwingUtilities.invokeLater(new Runnable(){
      public void run() {
        myConfigurable.selectNodeInTree(jdk != null ? jdk.getName() : null);
      }
    });
    setTitle(ProjectBundle.message("sdk.configure.title"));
    Disposer.register(myDisposable, new Disposable() {
      public void dispose() {
        if (myConfigurable != null) {
          myConfigurable.disposeUIResources();
          myConfigurable = null;
        }
      }
    });
    init();
  }

  public ProjectJdksEditor(Sdk jdk, Component parent){
    this(jdk, PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext()), parent);
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

  public Sdk getSelectedJdk(){
    return myProjectJdk;
  }

}