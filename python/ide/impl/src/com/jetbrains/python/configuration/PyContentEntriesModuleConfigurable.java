// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.configuration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleConfigurationStateImpl;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.module.PyContentEntriesEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.*;
import java.awt.*;

public class PyContentEntriesModuleConfigurable extends SearchableConfigurable.Parent.Abstract {
  private final Module myModule;
  private final JPanel myTopPanel = new JPanel(new BorderLayout());
  protected ModifiableRootModel myModifiableModel;
  protected PyContentEntriesEditor myEditor;

  public PyContentEntriesModuleConfigurable(final Module module) {
    myModule = module;
  }

  @Override
  public String getDisplayName() {
    return PyBundle.message("configurable.PyContentEntriesModuleConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settingsdialog.project.structure";
  }

  @Override
  public JComponent createComponent() {
    createEditor();
    return myTopPanel;
  }

  private void createEditor() {
    if (myModule == null) return;
    myModifiableModel =
      ReadAction.compute(() -> ModuleRootManager.getInstance(myModule).getModifiableModel());

    final ModuleConfigurationStateImpl moduleConfigurationState =
      new ModuleConfigurationStateImpl(myModule.getProject(), new DefaultModulesProvider(myModule.getProject())) {
        @Override
        public ModifiableRootModel getModifiableRootModel() {
          return myModifiableModel;
        }

        @Override
        public ModuleRootModel getCurrentRootModel() {
          return myModifiableModel;
        }
      };
    myEditor = createEditor(myModule, moduleConfigurationState);

    JComponent component = ReadAction.compute(() -> myEditor.createComponent());
    myTopPanel.add(component, BorderLayout.CENTER);
  }

  protected PyContentEntriesEditor createEditor(@NotNull Module module, @NotNull ModuleConfigurationStateImpl state) {
    return new PyContentEntriesEditor(module, state, true, JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE);
  }

  @Override
  public boolean isModified() {
    return myEditor != null && myEditor.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myEditor == null) return;
    final boolean editorWasModified = myEditor.isModified();
    myEditor.apply();
    if (editorWasModified) {
      ApplicationManager.getApplication().runWriteAction(() -> myModifiableModel.commit());
      resetEditor();
    }
  }

  @Override
  public void reset() {
    if (myEditor == null) return;
    if (myModifiableModel != null) {
      myModifiableModel.dispose();
    }
    resetEditor();
  }

  private void resetEditor() {
    myEditor.disposeUIResources();
    myTopPanel.remove(myEditor.getComponent());
    createEditor();
  }

  @Override
  public void disposeUIResources() {
    if (myEditor != null) {
      myEditor.disposeUIResources();
      myTopPanel.remove(myEditor.getComponent());
      myEditor = null;
    }
    if (myModifiableModel != null) {
      myModifiableModel.dispose();
      myModifiableModel = null;
    }
  }

  @Override
  protected Configurable[] buildConfigurables() {
    return new Configurable[0];
  }

  @Override
  @NotNull
  public String getId() {
    return "python.project.structure";
  }

}
