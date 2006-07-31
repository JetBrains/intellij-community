/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 04-Jun-2006
 */
public class ModuleConfigurable extends NamedConfigurable<Module> {
  private Module myModule;
  private ModulesConfigurator myConfigurator;
  private String myModuleName;

  public ModuleConfigurable(ModulesConfigurator modulesConfigurator,
                            Module module,
                            final Runnable updateTree) {
    super(true, updateTree);
    myModule = module;
    myModuleName = myModule.getName();
    myConfigurator = modulesConfigurator;
  }

  public void setDisplayName(String name) {
    name = name.trim();
    final ModifiableModuleModel modifiableModuleModel = myConfigurator.getModuleModel();
    if (Comparing.strEqual(name, myModuleName)) return; //nothing changed
    try {
      modifiableModuleModel.renameModule(myModule, name);
    }
    catch (ModuleWithNameAlreadyExists moduleWithNameAlreadyExists) {
      //do nothing
    }
    myConfigurator.moduleRenamed(myModuleName, name);
    myModuleName = name;
    myConfigurator.setModified(!Comparing.strEqual(myModuleName, myModule.getName()));
  }

  public Module getEditableObject() {
    return myModule;
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("project.roots.module.banner.text", myModuleName);
  }

  public String getDisplayName() {
    return myModuleName;
  }

  public Icon getIcon() {
    return myModule.getModuleType().getNodeIcon(false);
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    final ModuleEditor moduleEditor = getModuleEditor();
    return moduleEditor != null ? moduleEditor.getHelpTopic() : null;
  }


  public JComponent createOptionsPanel() {
    return getModuleEditor().getPanel();
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {
    //do nothing
  }

  public void reset() {
    //do nothing
  }

  public void disposeUIResources() {
    //do nothing
  }

  ModuleEditor getModuleEditor() {
    return myConfigurator.getModuleEditor(myModule);
  }
}
