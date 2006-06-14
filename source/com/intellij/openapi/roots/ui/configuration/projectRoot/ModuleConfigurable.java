/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.NamedConfigurable;
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
  
  public ModuleConfigurable(ModulesConfigurator modulesConfigurator,
                            Module module) {
    myModule = module;
    myConfigurator = modulesConfigurator;
  }

  public void setDisplayName(final String name) {
    //do nothing
  }

  public Module getEditableObject() {
    return myModule;
  }

  public String getBannerSlogan() {
    return myModule.getName();
  }

  public String getDisplayName() {
    return myModule.getName();
  }

  public Icon getIcon() {
    return myModule.getModuleType().getNodeIcon(false);
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return getModuleEditor().getHelpTopic();
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

  ModuleEditor getModuleEditor(){
    return myConfigurator.getModuleEditor(myModule);
  }
}
