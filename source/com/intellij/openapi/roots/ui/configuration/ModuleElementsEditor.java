package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 * Date: Oct 4, 2003
 * Time: 7:24:37 PM
 */
public abstract class ModuleElementsEditor implements ModuleConfigurationEditor {
  protected final Project myProject;
  protected final ModifiableRootModel myModel;
  protected JComponent myComponent;

  protected ModuleElementsEditor(Project project, ModifiableRootModel model) {
    myProject = project;
    myModel = model;
  }

  public boolean isModified() {
    boolean modelChanged = myModel == null? false : myModel.isChanged();
    return modelChanged;
  }

  public void apply() throws ConfigurationException {}
  public void reset() {}
  public void moduleStateChanged() {}
  public void disposeUIResources() {}

  // caching
  public final JComponent createComponent() {
    if (myComponent == null) {
      myComponent = createComponentImpl();
    }
    return myComponent;
  }


  public JComponent getComponent() {
    return createComponent();
  }

  protected abstract JComponent createComponentImpl();
}
