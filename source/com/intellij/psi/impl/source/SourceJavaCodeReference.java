package com.intellij.psi.impl.source;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeElement;

/**
 * This interface should be implemented by all PsiJavaCodeReference implementations
 * in source.
 * 
 * @author dsl
 */
public interface SourceJavaCodeReference {
  /**
   * @return text of class name (as much as there is in reference text, that is
   *      with qualifications if they are present)
   */
  String getClassNameText();

  /**
   * Helper method for ReferenceAdjuster. Tries to qualify this reference as if
   * it references <code>targetClass</code>. Does not check that it indeed references
   * targetClass
   * @param targetClass
   */
  void fullyQualify(PsiClass targetClass);

  boolean isQualified();

  TreeElement getTreeQualifier();
  PsiElement getQualifier();

  void dequalify();
}
