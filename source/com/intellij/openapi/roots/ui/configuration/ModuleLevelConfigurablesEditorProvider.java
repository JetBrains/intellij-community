/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;

import javax.swing.*;
import java.util.ArrayList;

public class ModuleLevelConfigurablesEditorProvider implements ModuleConfigurationEditorProvider, ModuleComponent {
  public static final ExtensionPointName<Configurable> MODULE_CONFIGURABLES = ExtensionPointName.create("com.intellij.moduleConfigurable");

  private final Module myModule;

  public ModuleLevelConfigurablesEditorProvider(Module module) {
    myModule = module;
  }

  public ModuleConfigurationEditor[] createEditors(ModuleConfigurationState state) {
    ArrayList<ModuleConfigurationEditor> result = new ArrayList<ModuleConfigurationEditor>();
    Configurable[] moduleConfigurables = myModule.getComponents(Configurable.class);
    for (final Configurable moduleConfigurable : moduleConfigurables) {
      result.add(new ConfigurableWrapper(moduleConfigurable));
    }
    for(Configurable configurable: Extensions.getExtensions(MODULE_CONFIGURABLES)) {
      result.add(new ConfigurableWrapper(configurable));
    }

    return result.toArray(new ModuleConfigurationEditor[result.size()]);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void moduleAdded() {
  }

  public String getComponentName() {
    return "ModuleLevelConfigurablesEditorProvider";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  private static class ConfigurableWrapper implements ModuleConfigurationEditor {
    private final Configurable myModuleConfigurable;

    public ConfigurableWrapper(Configurable moduleConfigurable) {
      myModuleConfigurable = moduleConfigurable;
    }

    public void saveData() {

    }

    public void moduleStateChanged() {
    }

    public String getDisplayName() {
      return myModuleConfigurable.getDisplayName();
    }

    public Icon getIcon() {
      return myModuleConfigurable.getIcon();
    }

    public String getHelpTopic() {
      return myModuleConfigurable.getHelpTopic();
    }

    public JComponent createComponent() {
      return myModuleConfigurable.createComponent();
    }

    public boolean isModified() {
      return myModuleConfigurable.isModified();
    }

    public void apply() throws ConfigurationException {
      myModuleConfigurable.apply();
    }

    public void reset() {
      myModuleConfigurable.reset();
    }

    public void disposeUIResources() {
      myModuleConfigurable.disposeUIResources();
    }
  }
}
