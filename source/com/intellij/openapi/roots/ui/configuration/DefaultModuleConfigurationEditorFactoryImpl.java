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
}
