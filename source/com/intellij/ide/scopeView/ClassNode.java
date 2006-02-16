package com.intellij.ide.scopeView;

import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 30-Jan-2006
 */
public class ClassNode extends PackageDependenciesNode {
  private SmartPsiElementPointer myClassElementPointer;
  private PsiFile myFile;

  public ClassNode(final PsiClass aClass) {
    myClassElementPointer = SmartPointerManager.getInstance(aClass.getProject()).createLazyPointer(aClass);
    myFile = aClass.getContainingFile();
  }

  @Nullable
  public PsiElement getPsiElement() {
    final PsiElement element = myClassElementPointer.getElement();
    return element != null && element.isValid() ? element : null;
  }


  public String toString() {
    final PsiClass aClass = (PsiClass)myClassElementPointer.getElement();
    return aClass != null && aClass.isValid() ? ClassPresentationUtil.getNameForClass(aClass, false) : null;
  }

  public Icon getOpenIcon() {
    return getIcon();
  }

  public Icon getClosedIcon() {
    return getIcon();
  }

  private Icon getIcon() {
    final PsiElement element = myClassElementPointer.getElement();
    return element != null && element.isValid() ? element.getIcon(Iconable.ICON_FLAG_VISIBILITY) : null;
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
    if (!(o instanceof ClassNode)) return false;

    final ClassNode classNode = (ClassNode)o;

    if (!Comparing.equal(getPsiElement(), classNode.getPsiElement())) return false;

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
