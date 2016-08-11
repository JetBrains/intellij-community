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

/**
 * @author Alexey
 */
package com.intellij.lang.properties.projectView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.DropTargetNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.ValidateableNode;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.editor.ResourceBundleAsVirtualFile;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ResourceBundleNode extends ProjectViewNode<ResourceBundle> implements ValidateableNode, DropTargetNode {
  public ResourceBundleNode(Project project, ResourceBundle resourceBundle, final ViewSettings settings) {
    super(project, resourceBundle, settings);
  }

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    List<PropertiesFile> propertiesFiles = getValue().getPropertiesFiles();
    Collection<AbstractTreeNode> children = new ArrayList<>();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      AbstractTreeNode node = new PsiFileNode(myProject, propertiesFile.getContainingFile(), getSettings());
      children.add(node);
    }
    return children;
  }

  public boolean contains(@NotNull VirtualFile file) {
    if (!file.isValid()) return false;
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(psiFile);
    return propertiesFile != null && getValue().getPropertiesFiles().contains(propertiesFile);
  }

  public VirtualFile getVirtualFile() {
    final List<PropertiesFile> list = getValue().getPropertiesFiles();
    if (!list.isEmpty()) {
      return list.get(0).getVirtualFile();
    }
    return null;
  }

  public void update(PresentationData presentation) {
    presentation.setIcon(AllIcons.Nodes.ResourceBundle);
    presentation.setPresentableText(PropertiesBundle.message("project.view.resource.bundle.tree.node.text", getValue().getBaseName()));
  }

  public boolean canNavigateToSource() {
    return true;
  }

  public boolean canNavigate() {
    return true;
  }

  public void navigate(final boolean requestFocus) {
    OpenFileDescriptor descriptor = new OpenFileDescriptor(getProject(), new ResourceBundleAsVirtualFile(getValue()));
    FileEditorManager.getInstance(getProject()).openTextEditor(descriptor, requestFocus);
  }

  public boolean isSortByFirstChild() {
    return true;
  }

  public Comparable getTypeSortKey() {
    return new PsiFileNode.ExtensionSortKey(StdFileTypes.PROPERTIES.getDefaultExtension());
  }

  @Override
  public boolean validate() {
    if (!super.validate()) {
      return false;
    }
    final ResourceBundle newBundle = getValue().getDefaultPropertiesFile().getResourceBundle();
    final ResourceBundle currentBundle = getValue();
    if (!Comparing.equal(newBundle, currentBundle)) {
      return false;
    }
    return !(currentBundle instanceof ResourceBundleImpl) || ((ResourceBundleImpl)currentBundle).isValid();
  }

  @Override
  public boolean isValid() {
    return getValue().getDefaultPropertiesFile().getContainingFile().isValid();
  }

  @Override
  public boolean canDrop(TreeNode[] sourceNodes) {
    for (TreeNode node : sourceNodes) {
      if (extractPropertiesFileFromNode(node) == null) return false;
    }
    return true;
  }

  @Override
  public void drop(TreeNode[] sourceNodes, DataContext dataContext) {
    MultiMap<ResourceBundle, PropertiesFile> bundleGrouping = new MultiMap<>();
    for (TreeNode sourceNode : sourceNodes) {
      final PropertiesFile propertiesFile = extractPropertiesFileFromNode(sourceNode);
      if (propertiesFile == null) return;
      bundleGrouping.putValue(propertiesFile.getResourceBundle(), propertiesFile);
    }
    final ResourceBundle resourceBundle = getValue();
    bundleGrouping.remove(resourceBundle);

    final ResourceBundleManager resourceBundleManager = ResourceBundleManager.getInstance(myProject);
    final List<PropertiesFile> toAddInResourceBundle = new ArrayList<>();

    for (Map.Entry<ResourceBundle, Collection<PropertiesFile>> entry : bundleGrouping.entrySet()) {
      toAddInResourceBundle.addAll(entry.getValue());
      final ResourceBundle currentBundle = entry.getKey();
      final Collection<PropertiesFile> propertiesFilesToMove = entry.getValue();
      if (currentBundle.getPropertiesFiles().size() - propertiesFilesToMove.size() > 0) {
        final String currentBundleBaseName = currentBundle.getBaseName();
        final ArrayList<PropertiesFile> files = new ArrayList<>(currentBundle.getPropertiesFiles());
        files.removeAll(propertiesFilesToMove);
        resourceBundleManager.dissociateResourceBundle(currentBundle);
        resourceBundleManager.combineToResourceBundle(files, currentBundleBaseName);
      }
    }

    toAddInResourceBundle.addAll(resourceBundle.getPropertiesFiles());
    final String baseName = resourceBundle.getBaseName();
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(getProject());
    fileEditorManager.closeFile(new ResourceBundleAsVirtualFile(resourceBundle));
    resourceBundleManager.dissociateResourceBundle(resourceBundle);
    final ResourceBundle updatedBundle = resourceBundleManager.combineToResourceBundleAndGet(toAddInResourceBundle, baseName);
    FileEditorManager.getInstance(myProject).openFile(new ResourceBundleAsVirtualFile(updatedBundle), true);
    ProjectView.getInstance(myProject).refresh();
  }

  @Override
  public void dropExternalFiles(PsiFileSystemItem[] sourceFileArray, DataContext dataContext) {
  }

  @Nullable
  private static PropertiesFile extractPropertiesFileFromNode(TreeNode node) {
    if (!(node instanceof DefaultMutableTreeNode)) return null;
    final Object userObject = ((DefaultMutableTreeNode) node).getUserObject();
    if (!(userObject instanceof PsiFileNode)) return null;
    final PsiFile file = ((PsiFileNode)userObject).getValue();
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
    if (propertiesFile == null || !file.getManager().isInProject(file) || !file.isValid()) return null;
    return propertiesFile;
  }
}