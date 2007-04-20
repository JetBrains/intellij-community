package com.intellij.openapi.module.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.UserDataHolderBase;

public class ModuleConfigurationStateImpl extends UserDataHolderBase implements ModuleConfigurationState {
  private ModulesProvider myProvider;
  private ModifiableRootModel myRootModel;
  private Project myProject;
  private FacetsProvider myFacetsProvider;

  public ModuleConfigurationStateImpl(Project project, ModulesProvider provider, ModifiableRootModel rootModel,
                                      final FacetsProvider facetsProvider) {
    myFacetsProvider = facetsProvider;
    myProvider = provider;
    myRootModel = rootModel;
    myProject = project;
  }

  public ModulesProvider getModulesProvider() {
    return myProvider;
  }

  public FacetsProvider getFacetsProvider() {
    return myFacetsProvider;
  }

  public ModifiableRootModel getRootModel() {
    return myRootModel;
  }

  public Project getProject() {
    return myProject;
  }
}