// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.*;
import java.util.stream.Stream;

public class ResourceBundleNode extends ProjectViewNode<PsiFile[]> implements ValidateableNode, DropTargetNode, ResourceBundleAwareNode {
  @NotNull
  private final ResourceBundle myBundle;

  public ResourceBundleNode(Project project, @NotNull ResourceBundle resourceBundle, final ViewSettings settings) {
    super(project, resourceBundle.getPropertiesFiles().stream().map(PropertiesFile::getContainingFile).toArray(PsiFile[]::new), settings);
    myBundle = resourceBundle;
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    PsiFile[] propertiesFiles = ObjectUtils.notNull(getValue());
    Collection<AbstractTreeNode> children = new ArrayList<>();
    for (PsiFile propertiesFile : propertiesFiles) {
      AbstractTreeNode node = new PsiFileNode(myProject, propertiesFile, getSettings());
      children.add(node);
    }
    return children;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (!file.isValid()) return false;
    PsiFile psiFile = PsiManager.getInstance(Objects.requireNonNull(getProject())).findFile(file);
    PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(psiFile);
    return propertiesFile != null && ArrayUtil.contains(psiFile, ObjectUtils.notNull(getValue()));
  }

  @Override
  public VirtualFile getVirtualFile() {
    final PsiFile[] list = ObjectUtils.notNull(getValue());
    if (list.length != 0) {
      return list[0].getVirtualFile();
    }
    return null;
  }

  @Override
  public void update(PresentationData presentation) {
    presentation.setIcon(AllIcons.Nodes.ResourceBundle);
    presentation.setPresentableText(PropertiesBundle.message("project.view.resource.bundle.tree.node.text", myBundle.getBaseName()));
  }

  @Override
  protected boolean shouldUpdateData() {
    return isValid() && super.shouldUpdateData();
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
    OpenFileDescriptor descriptor = new OpenFileDescriptor(Objects.requireNonNull(getProject()), new ResourceBundleAsVirtualFile(myBundle));
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
    final ResourceBundle newBundle = ObjectUtils.notNull(PropertiesImplUtil.getPropertiesFile(Objects.requireNonNull(getValue())[0])).getResourceBundle();
    final ResourceBundle currentBundle = myBundle;
    if (!Comparing.equal(newBundle, currentBundle)) {
      return false;
    }
    return ObjectUtils.notNull(currentBundle).isValid();
  }

  @Override
  public boolean isValid() {
    return Stream.of(ObjectUtils.notNull(getValue())).allMatch(PsiElement::isValid);
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
    bundleGrouping.remove(myBundle);

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

    toAddInResourceBundle.addAll(myBundle.getPropertiesFiles());
    final String baseName = myBundle.getBaseName();
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(Objects.requireNonNull(getProject()));
    fileEditorManager.closeFile(new ResourceBundleAsVirtualFile(myBundle));
    resourceBundleManager.dissociateResourceBundle(myBundle);
    final ResourceBundle updatedBundle = resourceBundleManager.combineToResourceBundleAndGet(toAddInResourceBundle, baseName);
    FileEditorManager.getInstance(getProject()).openFile(new ResourceBundleAsVirtualFile(updatedBundle), true);
    ProjectView.getInstance(getProject()).refresh();
  }

  @Override
  public void dropExternalFiles(PsiFileSystemItem[] sourceFileArray, DataContext dataContext) {
  }

  @NotNull
  @Override
  public ResourceBundle getResourceBundle() {
    return myBundle;
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