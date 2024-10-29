// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.projectView;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ResourceBundleGrouper implements TreeStructureProvider, DumbAware {
  private final Project myProject;

  public ResourceBundleGrouper(Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> parent, @NotNull final Collection<AbstractTreeNode<?>> children, final ViewSettings settings) {
    if (parent instanceof ResourceBundleNode) {
      return children;
    }

    return ReadAction.compute(() -> {
      List<AbstractTreeNode<?>> result = new ArrayList<>();
      List<PropertiesFile> dirPropertiesFiles = new SmartList<>();
      for (AbstractTreeNode<?> child : children) {
        Object f = child.getValue();
        if (f instanceof PsiFile && child.getClass() == PsiFileNode.class) { // process raw nodes only
          PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile((PsiFile)f);
          if (propertiesFile != null) {
            dirPropertiesFiles.add(propertiesFile);
            continue;
          }
        }
        result.add(child);
      }
      if (dirPropertiesFiles.isEmpty()) {
        return result;
      }
      appendPropertiesFilesNodes(dirPropertiesFiles, myProject, result::add, settings);
      return result;
    });
  }

  private static void appendPropertiesFilesNodes(@NotNull List<? extends PropertiesFile> files,
                                                 @NotNull Project project,
                                                 @NotNull Consumer<? super AbstractTreeNode<?>> nodeConsumer,
                                                 ViewSettings settings) {
    ResourceBundleManager manager = ResourceBundleManager.getInstance(project);

    MultiMap<Couple<String>, PropertiesFile> resourceBundles = new MultiMap<>();
    MultiMap<CustomResourceBundle, PropertiesFile> customResourceBundles = new MultiMap<>();

    for (PropertiesFile file : files) {
      final CustomResourceBundle customResourceBundle = manager.getCustomResourceBundle(file);
      if (customResourceBundle != null) {
        customResourceBundles.putValue(customResourceBundle, file);
      } else {
        String extension = file.getVirtualFile().getExtension();
        String baseName = manager.getBaseName(file.getContainingFile());
        resourceBundles.putValue(Couple.of(baseName, extension), file);
      }
    }

    for (Map.Entry<Couple<String>, Collection<PropertiesFile>> entry : resourceBundles.entrySet()) {
      Collection<PropertiesFile> bundleFiles = entry.getValue();
      PropertiesFile defaultFile = bundleFiles.iterator().next();
      nodeConsumer.consume(bundleFiles.size() == 1
                           ? new PsiFileNode(project, defaultFile.getContainingFile(), settings)
                           : new ResourceBundleNode(project, new ResourceBundleImpl(defaultFile), settings));
    }

    for (Map.Entry<CustomResourceBundle, Collection<PropertiesFile>> entry : customResourceBundles.entrySet()) {
      Collection<PropertiesFile> bundleFiles = entry.getValue();
      if (bundleFiles.size() == 1) {
        PropertiesFile representative = bundleFiles.iterator().next();
        nodeConsumer.consume(new CustomResourceBundlePropertiesFileNode(project, representative.getContainingFile(), settings));
      } else {
        nodeConsumer.consume(new ResourceBundleNode(project, entry.getKey(), settings));
      }
    }
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink, @NotNull Collection<? extends AbstractTreeNode<?>> selection) {
    sink.lazy(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, () -> {
      for (AbstractTreeNode<?> selectedElement : selection) {
        Object element = selectedElement.getValue();
        if (element instanceof ResourceBundle) {
          return new ResourceBundleDeleteProvider();
        }
      }
      return null;
    });
    sink.lazy(ResourceBundle.ARRAY_DATA_KEY, () -> {
      List<ResourceBundle> selectedElements = new ArrayList<>();
      for (AbstractTreeNode<?> node : selection) {
        Object value = node.getValue();
        if (value instanceof ResourceBundle) {
          selectedElements.add((ResourceBundle)value);
        }
      }
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new ResourceBundle[0]);
    });
  }
}
