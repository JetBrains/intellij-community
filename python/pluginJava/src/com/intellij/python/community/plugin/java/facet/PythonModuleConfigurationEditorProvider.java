// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.facet;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.DefaultModuleConfigurationEditorFactory;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.jetbrains.python.module.PyContentEntriesEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.ArrayList;
import java.util.List;

final class PythonModuleConfigurationEditorProvider implements ModuleConfigurationEditorProvider {
  @Override
  public @NotNull ModuleConfigurationEditor @NotNull [] createEditors(@NotNull ModuleConfigurationState state) {
    Module module = state.getCurrentRootModel().getModule();
    if (!(ModuleType.get(module) instanceof PythonModuleType)) {
      return ModuleConfigurationEditor.EMPTY;
    }

    DefaultModuleConfigurationEditorFactory editorFactory = DefaultModuleConfigurationEditorFactory.getInstance();
    List<ModuleConfigurationEditor> editors = new ArrayList<>();
    editors.add(new PyContentEntriesEditor(module, state, false, JavaSourceRootType.SOURCE));
    editors.add(editorFactory.createClasspathEditor(state));
    return editors.toArray(ModuleConfigurationEditor.EMPTY);
  }
}