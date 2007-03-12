package com.intellij.ide.scopeView.nodes;

import com.intellij.psi.PsiClass;
import com.intellij.psi.presentation.java.ClassPresentationUtil;

/**
 * User: anna
 * Date: 30-Jan-2006
 */
public class ClassNode extends BasePsiNode<PsiClass>{
  public ClassNode(final PsiClass aClass) {
    super(aClass);
  }

  public String toString() {
    final PsiClass aClass = (PsiClass)getPsiElement();
    return aClass != null && aClass.isValid() ? ClassPresentationUtil.getNameForClass(aClass, false) : "";
  }

  public int getWeight() {
    return 4;
  }
}
