/**
 * @author Alexey
 */
package com.intellij.lang.properties;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.editor.ResourceBundleAsVirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

class ResourceBundleNode extends ProjectViewNode<ResourceBundle>{
  public ResourceBundleNode(Project project, ResourceBundle resourceBundle, final ViewSettings settings) {
    super(project, resourceBundle, settings);
  }
  public Collection<AbstractTreeNode> getChildren() {
    List<PropertiesFile> propertiesFiles = getValue().getPropertiesFiles();
    List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      AbstractTreeNode node = new PsiFileNode(myProject, propertiesFile, getSettings());
      children.add(node);
    }
    return children;
  }

  public boolean contains(VirtualFile file) {
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    if (!(psiFile instanceof PropertiesFile)) return false;
    PropertiesFile propertiesFile = (PropertiesFile)psiFile;
    return getValue().getPropertiesFiles().contains(propertiesFile);
  }

  public void update(PresentationData presentation) {
    presentation.setIcons(PropertiesFileType.FILE_ICON);
    presentation.setPresentableText("Resource Bundle '"+getValue().getBaseName()+"'");
  }

  public boolean canNavigateToSource() {
    return true;
  }

  public void navigate(final boolean requestFocus) {
    OpenFileDescriptor descriptor = new OpenFileDescriptor(getProject(), new ResourceBundleAsVirtualFile(getValue()));
    FileEditorManager.getInstance(getProject()).openTextEditor(descriptor, requestFocus);
  }
}