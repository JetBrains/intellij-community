/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.properties.projectView;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ResourceBundleGrouper implements TreeStructureProvider, DumbAware {
  private final Project myProject;

  public ResourceBundleGrouper(Project project) {
    myProject = project;
  }

  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    if (parent instanceof ResourceBundleNode) return children;

    Map<ResourceBundle,Collection<PropertiesFile>> childBundles = new THashMap<ResourceBundle, Collection<PropertiesFile>>();
    for (AbstractTreeNode child : children) {
      Object f = child.getValue();
      if (f instanceof PropertiesFile) {
        PropertiesFile propertiesFile = (PropertiesFile)f;
        ResourceBundle bundle = propertiesFile.getResourceBundle();
        Collection<PropertiesFile> files = childBundles.get(bundle);
        if (files == null) {
          files = new SmartList<PropertiesFile>();
          childBundles.put(bundle, files);
        }
        files.add(propertiesFile);
      }
    }

    List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    for (Map.Entry<ResourceBundle, Collection<PropertiesFile>> entry : childBundles.entrySet()) {
      ResourceBundle resourceBundle = entry.getKey();
      Collection<PropertiesFile> files = entry.getValue();
      if (files.size() != 1) {
        result.add(new ResourceBundleNode(myProject, resourceBundle, settings));
      }
    }
    for (AbstractTreeNode child : children) {
      Object f = child.getValue();
      if (f instanceof PropertiesFile) {
        PropertiesFile propertiesFile = (PropertiesFile)f;
        ResourceBundle bundle = propertiesFile.getResourceBundle();
        if (childBundles.get(bundle).size() != 1) {
          continue;
        }
      }
      result.add(child);
    }

    return result;
  }

  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    if (selected == null) return null;
    for (AbstractTreeNode selectedElement : selected) {
      Object element = selectedElement.getValue();
      if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataName)) {
        if (element instanceof ResourceBundle) {
          return new ResourceBundleDeleteProvider((ResourceBundle)element);
        }
      }
    }
    if (ResourceBundle.ARRAY_DATA_KEY.is(dataName)) {
      final List<ResourceBundle> selectedElements = new ArrayList<ResourceBundle>();
      for (AbstractTreeNode node : selected) {
        final Object value = node.getValue();
        if (value instanceof ResourceBundle) {
          selectedElements.add((ResourceBundle)value);
        }
      }
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new ResourceBundle[selectedElements.size()]);
    }
    return null;
  }
}
