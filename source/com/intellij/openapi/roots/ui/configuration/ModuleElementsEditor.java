package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Disposer;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 * Date: Oct 4, 2003
 * Time: 7:24:37 PM
 */
public abstract class ModuleElementsEditor implements ModuleConfigurationEditor {
  protected final Project myProject;
  protected final ModifiableRootModel myModel;
  protected JComponent myComponent;
  private List<Disposable> myDisposables = new ArrayList<Disposable>();

  protected ModuleElementsEditor(Project project, ModifiableRootModel model) {
    myProject = project;
    myModel = model;
  }

  public boolean isModified() {
    return myModel != null && myModel.isChanged();
  }

  public void apply() throws ConfigurationException {}
  public void reset() {}
  public void moduleStateChanged() {}

  public void disposeUIResources() {
    for (Disposable disposable : myDisposables) {
      Disposer.dispose(disposable);
    }
    myDisposables.clear();
  }

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

  protected void registerDisposable(Disposable disposable) {
    myDisposables.add(disposable);
  }

  protected abstract JComponent createComponentImpl();
}
