/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 28, 2004
 */
public class DefaultModuleConfigurationEditorFactoryImpl extends DefaultModuleConfigurationEditorFactory {
  public ModuleConfigurationEditor createModuleContentRootsEditor(ModuleConfigurationState state) {
    final ModifiableRootModel rootModel = state.getRootModel();
    final Module module = rootModel.getModule();
    final String moduleName = module.getName();
    return new ContentEntriesEditor(state.getProject(), moduleName, rootModel, state.getModulesProvider());
  }

  public @Deprecated ModuleConfigurationEditor createLibrariesEditor(ModuleConfigurationState state) {
    return null;
  }

  public @Deprecated ModuleConfigurationEditor createDependenciesEditor(ModuleConfigurationState state) {
    return new DependenciesEditor(state.getProject(), state.getRootModel(), state.getModulesProvider());
  }

  public @Deprecated ModuleConfigurationEditor createOrderEntriesEditor(ModuleConfigurationState state) {
    return new OrderEntryEditor(state.getProject(), state.getRootModel());
  }

  public ModuleConfigurationEditor createClasspathEditor(ModuleConfigurationState state) {
    return new ClasspathEditor(state.getProject(), state.getRootModel(), state.getModulesProvider());
  }

  public ModuleConfigurationEditor createJavadocEditor(ModuleConfigurationState state) {
    return new JavadocEditor(state.getProject(), state.getRootModel());
  }

  public ModuleConfigurationEditor createOutputEditor(ModuleConfigurationState state) {
    return new OutputEditor(state.getProject(), state.getRootModel());
  }

  @Deprecated
  public ModuleConfigurationEditor createCompilerOutputEditor(ModuleConfigurationState state) {
    return new BuildElementsEditor(state.getProject(), state.getRootModel());
  }

  @NotNull
  public String getComponentName() {
    return "DefaultModuleConfigurationEditorFactory";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }
}
