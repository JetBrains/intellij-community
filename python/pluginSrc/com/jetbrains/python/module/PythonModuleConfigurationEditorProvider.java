package com.jetbrains.python.module;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.roots.ui.configuration.DefaultModuleConfigurationEditorFactory;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.module.Module;

import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public class PythonModuleConfigurationEditorProvider implements ModuleConfigurationEditorProvider {
  public ModuleConfigurationEditor[] createEditors(final ModuleConfigurationState state) {
    final Module module = state.getRootModel().getModule();
    if (!(ModuleType.get(module) instanceof PythonModuleType)) return ModuleConfigurationEditor.EMPTY;
    final DefaultModuleConfigurationEditorFactory editorFactory = DefaultModuleConfigurationEditorFactory.getInstance();
    final List<ModuleConfigurationEditor> editors = new ArrayList<ModuleConfigurationEditor>();
    editors.add(editorFactory.createModuleContentRootsEditor(state));
    editors.add(editorFactory.createClasspathEditor(state));
    return editors.toArray(new ModuleConfigurationEditor[editors.size()]);
  }
}
