/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class ProjectConfigurableContext extends FacetEditorContextBase {
  private Module myModule;
  private boolean myNewFacet;

  public ProjectConfigurableContext(final @NotNull Module module, final boolean newFacet) {
    super(module.getProject());
    myNewFacet = newFacet;
    myModule = module;
  }

  public Library[] getLibraries() {
    return super.getLibraries();
  }

  @Nullable
  public ModuleBuilder getModuleBuilder() {
    return null;
  }

  public boolean isNewFacet() {
    return myNewFacet;
  }

  public Module getModule() {
    return myModule;
  }
}
