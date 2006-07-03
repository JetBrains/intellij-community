/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.scopeView;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 30-Jan-2006
 */
public class BasePsiNode<T extends PsiMember> extends PackageDependenciesNode {
  private SmartPsiElementPointer myPsiElementPointer;
  private PsiFile myFile;

  public BasePsiNode(final T element) {
    myPsiElementPointer = SmartPointerManager.getInstance(element.getProject()).createLazyPointer(element);
    myFile = element.getContainingFile();
    setUserObject(toString());
  }

  @Nullable
  public PsiElement getPsiElement() {
    final PsiElement element = myPsiElementPointer.getElement();
    return element != null && element.isValid() ? element : null;
  }

  public Icon getOpenIcon() {
    return getIcon();
  }

  public Icon getClosedIcon() {
    return getIcon();
  }

  private Icon getIcon() {
    final PsiElement element = myPsiElementPointer.getElement();
    return element != null && element.isValid() ? element.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS) : null;
  }

  public FileStatus getStatus() {
    return FileStatusManager.getInstance(myFile.getProject()).getStatus(myFile.getVirtualFile());
  }

  public int getWeight() {
    return 4;
  }

  public int getContainingFiles() {
    return 0;
  }

  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof BasePsiNode)) return false;

    final BasePsiNode methodNode = (BasePsiNode)o;

    if (!Comparing.equal(getPsiElement(), methodNode.getPsiElement())) return false;

    return true;
  }

  public int hashCode() {
    PsiElement psiElement = getPsiElement();
    return psiElement == null ? 0 : psiElement.hashCode();
  }

  public PsiFile getContainingFile() {
    return myFile;
  }
}
