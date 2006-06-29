/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 04-Jun-2006
 */
public class ModuleConfigurable implements NamedConfigurable<Module> {
  private Module myModule;
  private ModulesConfigurator myConfigurator;
  private String myModuleName;

  public ModuleConfigurable(ModulesConfigurator modulesConfigurator, Module module) {
    myModule = module;
    myModuleName = myModule.getName();
    myConfigurator = modulesConfigurator;
  }

  public void setDisplayName(final String name) {
    final ModifiableModuleModel modifiableModuleModel = myConfigurator.getModuleModel();
    try {
      modifiableModuleModel.renameModule(myModule, name);
    }
    catch (ModuleWithNameAlreadyExists moduleWithNameAlreadyExists) {
      Messages.showErrorDialog(getModuleEditor().getPanel(), IdeBundle.message("error.module.already.exists", name),
                               IdeBundle.message("title.rename.module"));
      return;
    }
    myModuleName = name;
    myConfigurator.setModified(!Comparing.strEqual(myModule.getName(), myModuleName));
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

  public JComponent createComponent() {
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
