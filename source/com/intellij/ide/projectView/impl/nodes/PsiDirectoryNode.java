package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;

import java.util.Collection;

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
    return VfsUtil.isAncestor(getValue().getVirtualFile(), file, false);
  }
}
