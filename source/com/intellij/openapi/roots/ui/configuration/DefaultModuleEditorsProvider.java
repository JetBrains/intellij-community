package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;

import java.util.ArrayList;
import java.util.List;

public class DefaultModuleEditorsProvider implements ModuleComponent, ModuleConfigurationEditorProvider {
  public String getComponentName() {
    return "DefaultModuleEditorsProvider";
  }

  public void initComponent() {}
  public void disposeComponent() {}
  public void projectOpened() {}
  public void projectClosed() {}
  public void moduleAdded() {}

  public ModuleConfigurationEditor[] createEditors(ModuleConfigurationState state) {
    ModifiableRootModel rootModel = state.getRootModel();
    Module module = rootModel.getModule();
    String moduleName = module.getName();
    ModulesProvider provider = state.getModulesProvider();
    Project project = state.getProject();
    List<ModuleConfigurationEditor> editors = new ArrayList<ModuleConfigurationEditor>();
    editors.add(new ContentEntriesEditor(project, moduleName, rootModel, provider));
    editors.add(new LibrariesEditor(project, rootModel));
    if (provider.getModules().length > 1) {
      editors.add(new DependenciesEditor(project, rootModel, provider));
    }
    editors.add(new OrderEntryEditor(project, rootModel));
    editors.add(new JavadocEditor(project, rootModel));
    return editors.toArray(new ModuleConfigurationEditor[editors.size()]);
  }
}