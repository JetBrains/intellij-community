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
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.ValidateableNode;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.ResourceBundleAsVirtualFile;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ResourceBundleNode extends ProjectViewNode<ResourceBundle> implements ValidateableNode{
  public ResourceBundleNode(Project project, ResourceBundle resourceBundle, final ViewSettings settings) {
    super(project, resourceBundle, settings);
  }

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    List<PropertiesFile> propertiesFiles = getValue().getPropertiesFiles();
    Collection<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
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
}
