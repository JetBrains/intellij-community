/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetInfo;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.ProjectSettingsContext;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class ProjectConfigurableContext extends FacetEditorContextBase implements ProjectSettingsContext {
  private Module myModule;
  private final Facet myFacet;
  private boolean myNewFacet;
  private ModuleConfigurationState myModuleConfigurationState;

  public ProjectConfigurableContext(final @NotNull FacetInfo facetInfo, final @NotNull Facet facet, final boolean isNewFacet,
                                    @Nullable FacetEditorContext parentContext,
                                    final ModuleConfigurationState state, final UserDataHolder sharedModuleData,
                                    final UserDataHolder sharedProjectData) {
    super(facetInfo, parentContext, state.getFacetsProvider(), state.getModulesProvider(), sharedModuleData, sharedProjectData);
    myModuleConfigurationState = state;
    myFacet = facet;
    myNewFacet = isNewFacet;
    myModule = facet.getModule();
  }

  @Nullable
  public ModuleBuilder getModuleBuilder() {
    return null;
  }

  public boolean isNewFacet() {
    return myNewFacet;
  }

  @NotNull
  public Project getProject() {
    return myModule.getProject();
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public Facet getFacet() {
    return myFacet;
  }

  @Nullable
  public Facet getParentFacet() {
    return myFacet.getUnderlyingFacet();
  }

  @NotNull
  public ModifiableRootModel getModifiableRootModel() {
    return myModuleConfigurationState.getRootModel();
  }

  @Nullable
  public WizardContext getWizardContext() {
    return null;
  }
}
