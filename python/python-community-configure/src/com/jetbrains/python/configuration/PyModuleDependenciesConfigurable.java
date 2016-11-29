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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.util.Function;
import com.intellij.util.ui.EditableListModelDecorator;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class PyModuleDependenciesConfigurable implements UnnamedConfigurable {
  private final Module myModule;
  private List<Module> myInitialDependencies;
  private JPanel myMainPanel;
  private JPanel myListHolderPanel;
  private CheckBoxList<Module> myDependenciesList;

  public PyModuleDependenciesConfigurable(Module module) {
    myModule = module;
    myDependenciesList = new CheckBoxList<>();
    resetModel();
    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myDependenciesList,
                                                                  new EditableListModelDecorator((DefaultListModel) myDependenciesList.getModel()));
    decorator.disableRemoveAction();
    myListHolderPanel.add(decorator.createPanel(), BorderLayout.CENTER);
  }

  private void resetModel() {
    List<Module> possibleDependencies = new ArrayList<>();
    myInitialDependencies = Arrays.asList(ModuleRootManager.getInstance(myModule).getDependencies());
    possibleDependencies.addAll(myInitialDependencies);
    for (Module otherModule : ModuleManager.getInstance(myModule.getProject()).getModules()) {
      if (!possibleDependencies.contains(otherModule) && otherModule != myModule) {
        possibleDependencies.add(otherModule);
      }
    }
    myDependenciesList.setItems(possibleDependencies, module -> module.getName());
    myDependenciesList.setBorder(null);
    for (Module dependency : myInitialDependencies) {
      myDependenciesList.setItemSelected(dependency, true);
    }
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    return !collectDependencies().equals(myInitialDependencies);
  }

  private List<Module> collectDependencies() {
    List<Module> result = new ArrayList<>();
    for (int i = 0; i < myDependenciesList.getItemsCount(); i++) {
      Module module = (Module)myDependenciesList.getItemAt(i);
      if (myDependenciesList.isItemSelected(module)) {
        result.add(module);
      }
    }
    return result;
  }

  @Override
  public void apply() throws ConfigurationException {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
      List<ModuleOrderEntry> entries = new ArrayList<>();
      for (OrderEntry entry : model.getOrderEntries()) {
        if (entry instanceof ModuleOrderEntry) {
          entries.add((ModuleOrderEntry) entry);
        }
      }
      for (ModuleOrderEntry entry : entries) {
        model.removeOrderEntry(entry);
      }
      for (Module module : collectDependencies()) {
        model.addModuleOrderEntry(module);
      }
      model.commit();
    });
  }

  @Override
  public void reset() {
    resetModel();
  }

  @Override
  public void disposeUIResources() {
  }
}
