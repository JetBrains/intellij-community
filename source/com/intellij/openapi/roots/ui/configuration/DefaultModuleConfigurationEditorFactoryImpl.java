/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.roots.ModifiableRootModel;

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

  public ModuleConfigurationEditor createLibrariesEditor(ModuleConfigurationState state) {
    return new LibrariesEditor(state.getProject(), state.getRootModel());
  }

  public ModuleConfigurationEditor createDependenciesEditor(ModuleConfigurationState state) {
    return new DependenciesEditor(state.getProject(), state.getRootModel(), state.getModulesProvider());
  }

  public ModuleConfigurationEditor createOrderEntriesEditor(ModuleConfigurationState state) {
    return new OrderEntryEditor(state.getProject(), state.getRootModel());
  }

  public ModuleConfigurationEditor createJavadocEditor(ModuleConfigurationState state) {
    return new JavadocEditor(state.getProject(), state.getRootModel());
  }

  public String getComponentName() {
    return "DefaultModuleConfigurationEditorFactory";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }
}
