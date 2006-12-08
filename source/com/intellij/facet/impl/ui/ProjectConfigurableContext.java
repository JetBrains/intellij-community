/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ProjectConfigurableContext extends FacetEditorContextBase {
  private Module myModule;

  public ProjectConfigurableContext(final @NotNull Module module) {
    super(module.getProject());
    myModule = module;
  }

  @Nullable
  public ModuleBuilder getModuleBuilder() {
    return null;
  }

  public boolean isFacetCreating() {
    return false;
  }

  public Module getModule() {
    return myModule;
  }
}
