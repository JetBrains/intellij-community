// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.DefaultModuleConfigurationEditorFactory;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.ArrayList;
import java.util.List;


public final class PythonModuleConfigurationEditorProvider implements ModuleConfigurationEditorProvider {
  @Override
  public ModuleConfigurationEditor[] createEditors(final ModuleConfigurationState state) {
    final Module module = state.getCurrentRootModel().getModule();
    if (!(ModuleType.get(module) instanceof PythonModuleType)) return ModuleConfigurationEditor.EMPTY;
    final DefaultModuleConfigurationEditorFactory editorFactory = DefaultModuleConfigurationEditorFactory.getInstance();
    final List<ModuleConfigurationEditor> editors = new ArrayList<>();
    editors.add(new PyContentEntriesEditor(module, state, false, JavaSourceRootType.SOURCE));
    editors.add(editorFactory.createClasspathEditor(state));
    return editors.toArray(ModuleConfigurationEditor.EMPTY);
  }
}