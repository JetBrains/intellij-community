package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ContentEntriesEditor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public class PsiDirectoryNode extends BasePsiNode<PsiDirectory> {
  public PsiDirectoryNode(Project project, PsiDirectory value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  protected void updateImpl(PresentationData data) {
    PackageUtil.updatePsiDirectoryData(data, getProject(), getValue(), getSettings(), getParentValue(), this);
  }

  public Collection<AbstractTreeNode> getChildrenImpl() {
    return PackageUtil.getDirectoryChildren(getValue(), getSettings(), true);
  }

  public String getTestPresentation() {
    return "PsiDirectory: " + getValue().getName();
  }

  public boolean isFQNameShown() {
    return PackageUtil.isFQNameShown(getValue(), getParentValue(), getSettings());
  }

  public boolean contains(@NotNull VirtualFile file) {
    final PsiDirectory value = getValue();
    if (value == null) {
      return false;
    }

    if (!VfsUtil.isAncestor(value.getVirtualFile(), file, false)) {
      return false;
    }
    final Project project = value.getManager().getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module module = fileIndex.getModuleForFile(value.getVirtualFile());
    if (module == null) {
      return fileIndex.getModuleForFile(file) == null;
    }
    final ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
    
    return moduleFileIndex.isInContent(file);
  }

  public VirtualFile getVirtualFile() {
    PsiDirectory directory = getValue();
    if (directory == null) return null;
    return directory.getVirtualFile();
  }

  public boolean canRepresent(final Object element) {
    if (super.canRepresent(element)) return true;
    PsiDirectory directory = getValue();
    if (directory == null) return false;
    if (element instanceof PackageElement) {
      final PackageElement packageElement = (PackageElement)element;
      return Arrays.asList(packageElement.getPackage().getDirectories()).contains(directory);
    }
    if (element instanceof VirtualFile) {
      VirtualFile vFile = (VirtualFile) element;
      return directory.getVirtualFile() == vFile;
    }
    return false;
  }

  public boolean canNavigate() {
    VirtualFile virtualFile = getVirtualFile();
    return virtualFile != null && (PackageUtil.isSourceOrTestRoot(virtualFile, getProject()) || PackageUtil.isLibraryRoot(virtualFile, getProject()));
  }

  public boolean canNavigateToSource() {
    return false;
  }

  public void navigate(final boolean requestFocus) {
    Module module = ModuleUtil.findModuleForPsiElement(getValue());
    if (module != null) {
      ModulesConfigurator.showDialog(getProject(), module.getName(), ContentEntriesEditor.NAME, false);
    }
  }
}
