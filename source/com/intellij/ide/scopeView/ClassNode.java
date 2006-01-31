package com.intellij.ide.scopeView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Iconable;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 30-Jan-2006
 */
public class ClassNode extends PackageDependenciesNode {
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.scopeView.ClassNode");


  private SmartPsiElementPointer myClassElementPointer;

  public ClassNode(final PsiClass aClass) {
    myClassElementPointer =
      SmartPointerManager.getInstance(aClass.getProject()).createLazyPointer(aClass);
  }

  @Nullable
  public PsiElement getPsiElement() {
    return myClassElementPointer.getElement();
  }


  public String toString() {
    return ClassPresentationUtil.getNameForClass((PsiClass)myClassElementPointer.getElement(), false);
  }

  public Icon getOpenIcon() {
    return getIcon();
  }

  public Icon getClosedIcon() {
    return getIcon();
  }

  private Icon getIcon() {
    return myClassElementPointer.getElement().getIcon(Iconable.ICON_FLAG_VISIBILITY);
  }

  public int getWeight() {
    return 5;
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

    if (!myClassElementPointer.equals(classNode.myClassElementPointer)) return false;

    return true;
  }

  public int hashCode() {
    return myClassElementPointer.hashCode();
  }

}
