package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import javax.swing.*;
import java.util.Collection;
import java.util.ArrayList;

public abstract class BasePsiNode <Type extends PsiElement> extends ProjectViewNode<Type> {
  static private final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.impl.nodes.BasePsiNode");

  private SmartPsiElementPointer mySmartPointer;

  protected BasePsiNode(Project project, Type value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
    mySmartPointer = SmartPointerManager.getInstance(getProject()).createSmartPsiElementPointer(getValue());
  }

  public final Collection<AbstractTreeNode> getChildren() {
    Type value = getValue();
    if (value == null) return new ArrayList<AbstractTreeNode>();
    boolean valid = value.isValid();
    if (!LOG.assertTrue(valid)) {
      return null;
    }
    return getChildrenImpl();
  }

  protected abstract Collection<AbstractTreeNode> getChildrenImpl();

  protected boolean isMarkReadOnly() {
    return getParentValue() instanceof PsiDirectory;
  }

  public FileStatus getFileStatus() {
    if (getParentValue() instanceof PsiDirectory) {
      VirtualFile file = getVirtualFileForValue();
      if (file != null) {
        return FileStatusManager.getInstance(getProject()).getStatus(file);
      }
    }
    return FileStatus.NOT_CHANGED;

  }

  private VirtualFile getVirtualFileForValue() {
    Type value = getValue();
    if (value == null) return null;
    if (value instanceof PsiDirectory) {
      return ((PsiDirectory)value).getVirtualFile();
    }
    else {
      PsiFile containingFile = value.getContainingFile();
      if (containingFile == null) {
        return null;
      }
      else {
        return containingFile.getVirtualFile();
      }
    }
  }
  // Should be called in atomic action

  protected abstract void updateImpl(PresentationData data);


  public void update(PresentationData data) {
    setValue((Type)mySmartPointer.getElement());
    if (getValue() == null) return;

    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (isMarkReadOnly()) {
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }

    Icon icon = getValue().getIcon(flags);
    data.setClosedIcon(icon);
    data.setOpenIcon(icon);
    data.setLocationString(myLocationString);
    data.setPresentableText(myName);
    updateImpl(data);
  }

  public boolean contains(VirtualFile file) {
    PsiFile containingFile = getValue().getContainingFile();
    return containingFile.getVirtualFile().equals(file);
  }

  public void navigate(boolean requestFocus) {
    if (canNavigate()) {
        ((NavigationItem)getValue()).navigate(requestFocus);
    }
  }

  public boolean canNavigate() {
    return getValue() instanceof NavigationItem;
  }

  public static VirtualFile getVirtualFile(PsiElement element) {
    return element instanceof PsiDirectory
      ? ((PsiDirectory)element).getVirtualFile()
      : element.getContainingFile().getVirtualFile();
  }
}
