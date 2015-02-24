/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.configuration;

import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleConfigurationStateImpl;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.util.Computable;
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
    return "Project Structure";
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
    myModifiableModel = ApplicationManager.getApplication().runReadAction(new Computable<ModifiableRootModel>() {
      @Override
      public ModifiableRootModel compute() {
        return ModuleRootManager.getInstance(myModule).getModifiableModel();
      }
    });

    final ModuleConfigurationStateImpl moduleConfigurationState =
      new ModuleConfigurationStateImpl(myModule.getProject(), new DefaultModulesProvider(myModule.getProject())) {
        @Override
        public ModifiableRootModel getRootModel() {
          return myModifiableModel;
        }

        @Override
        public FacetsProvider getFacetsProvider() {
          return DefaultFacetsProvider.INSTANCE;
        }
      };
    myEditor = createEditor(myModule, moduleConfigurationState);

    JComponent component = ApplicationManager.getApplication().runReadAction(new Computable<JComponent>() {
      @Override
      public JComponent compute() {
        return myEditor.createComponent();
      }
    });
    myTopPanel.add(component, BorderLayout.CENTER);
  }

  protected PyContentEntriesEditor createEditor(@NotNull Module module, @NotNull ModuleConfigurationStateImpl state) {
    return new PyContentEntriesEditor(module, state, JavaSourceRootType.SOURCE);
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
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          myModifiableModel.commit();
        }
      });
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
