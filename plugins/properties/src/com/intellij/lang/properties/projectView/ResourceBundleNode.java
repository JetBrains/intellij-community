// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

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
import com.intellij.lang.properties.ResourceBundle;
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
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.*;

public class ResourceBundleNode extends ProjectViewNode<ResourceBundle> implements ValidateableNode, DropTargetNode {
  public ResourceBundleNode(Project project, @NotNull ResourceBundle resourceBundle, final ViewSettings settings) {
    super(project, resourceBundle, settings);
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    List<PropertiesFile> propertiesFiles = ObjectUtils.notNull(getValue()).getPropertiesFiles();
    Collection<AbstractTreeNode> children = new ArrayList<>();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      AbstractTreeNode node = new PsiFileNode(myProject, propertiesFile.getContainingFile(), getSettings());
      children.add(node);
    }
    return children;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (!file.isValid()) return false;
    PsiFile psiFile = PsiManager.getInstance(Objects.requireNonNull(getProject())).findFile(file);
    PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(psiFile);
    return propertiesFile != null && ObjectUtils.notNull(getValue()).getPropertiesFiles().contains(propertiesFile);
  }

  @Override
  public VirtualFile getVirtualFile() {
    final List<PropertiesFile> list = ObjectUtils.notNull(getValue()).getPropertiesFiles();
    if (!list.isEmpty()) {
      return list.get(0).getVirtualFile();
    }
    return null;
  }

  @Override
  public void update(PresentationData presentation) {
    presentation.setIcon(AllIcons.Nodes.ResourceBundle);
    presentation.setPresentableText(PropertiesBundle.message("project.view.resource.bundle.tree.node.text", ObjectUtils.notNull(getValue()).getBaseName()));
  }

  @Override
  public boolean canNavigateToSource() {
    return true;
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Override
  public void navigate(final boolean requestFocus) {
    OpenFileDescriptor descriptor = new OpenFileDescriptor(Objects.requireNonNull(getProject()), new ResourceBundleAsVirtualFile(ObjectUtils.notNull(getValue())));
    FileEditorManager.getInstance(getProject()).openTextEditor(descriptor, requestFocus);
  }

  @Override
  public boolean isSortByFirstChild() {
    return true;
  }

  @Override
  public Comparable getTypeSortKey() {
    return new PsiFileNode.ExtensionSortKey(StdFileTypes.PROPERTIES.getDefaultExtension());
  }

  @Override
  public boolean validate() {
    if (!super.validate()) {
      return false;
    }
    final ResourceBundle newBundle = ObjectUtils.notNull(getValue()).getDefaultPropertiesFile().getResourceBundle();
    final ResourceBundle currentBundle = getValue();
    if (!Comparing.equal(newBundle, currentBundle)) {
      return false;
    }
    return ObjectUtils.notNull(currentBundle).isValid();
  }

  @Override
  public boolean isValid() {
    return ObjectUtils.notNull(getValue()).isValid();
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
    final ResourceBundle resourceBundle = ObjectUtils.notNull(getValue());
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
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(Objects.requireNonNull(getProject()));
    fileEditorManager.closeFile(new ResourceBundleAsVirtualFile(resourceBundle));
    resourceBundleManager.dissociateResourceBundle(resourceBundle);
    final ResourceBundle updatedBundle = resourceBundleManager.combineToResourceBundleAndGet(toAddInResourceBundle, baseName);
    FileEditorManager.getInstance(getProject()).openFile(new ResourceBundleAsVirtualFile(updatedBundle), true);
    ProjectView.getInstance(getProject()).refresh();
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