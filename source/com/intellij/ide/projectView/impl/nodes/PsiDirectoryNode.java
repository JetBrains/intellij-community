package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiDirectory;

import java.util.Collection;
import java.util.Arrays;

public class PsiDirectoryNode extends BasePsiNode<PsiDirectory> {
  public PsiDirectoryNode(Project project, PsiDirectory value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public PsiDirectoryNode(Project project, Object value, ViewSettings viewSettings) {
    this(project, (PsiDirectory)value, viewSettings);
  }

  public void updateImpl(PresentationData data) {
    PackageUtil.updatePsiDirectoryData(data, getProject(), getValue(), getSettings(), getParentValue(), this);

  }

  public Collection<AbstractTreeNode> getChildrenImpl() {
    return PackageUtil.getDirectoryChildren(getValue(), getSettings(), true);
  }


  public String getTestPresentation() {
    return "PsiDirectory: " + getValue().getName();
  }

  protected String getRootName() {
    return getValue().getVirtualFile().getPresentableUrl();
  }

  public boolean isFQNameShown() {

    return PackageUtil.isFQNameShown(getValue(), getParentValue(), getSettings());

  }

  public boolean contains(VirtualFile file) {
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
    if (module == null) return true;
    final ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
    
    return moduleFileIndex.isInContent(file);
  }

  public VirtualFile getVirtualFile() {
    return getValue().getVirtualFile();
  }

  public boolean canRepresent(final Object element) {
    if (super.canRepresent(element)) return true;
    if (getValue() == null) return false;
    if (element instanceof PackageElement) {
      final PackageElement packageElement = ((PackageElement)element);
      return Arrays.asList(packageElement.getPackage().getDirectories()).contains(getValue());
    }
    return false;
  }

  }
