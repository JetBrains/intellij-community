package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.UserDataHolderBase;

public class ModuleConfigurationStateImpl extends UserDataHolderBase implements ModuleConfigurationState {
  private Module myModule;
  private ModulesProvider myProvider;
  private ModifiableRootModel myRootModel;
  private Project myProject;

  public ModuleConfigurationStateImpl(Project project, Module module, ModulesProvider provider, ModifiableRootModel rootModel) {
    myModule = module;
    myProvider = provider;
    myRootModel = rootModel;
    myProject = project;
  }

  public Module getModule() {
    return myModule;
  }

  public ModulesProvider getModulesProvider() {
    return myProvider;
  }

  public ModifiableRootModel getRootModel() {
    return myRootModel;
  }

  public Project getProject() {
    return myProject;
  }
}