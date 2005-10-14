/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 05.06.2002
 * Time: 12:42:12
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.rename;

import com.intellij.psi.PsiElement;

public abstract class UnresolvableCollisionUsageInfo extends CollisionUsageInfo {
  public UnresolvableCollisionUsageInfo(PsiElement element, PsiElement referencedElement) {
    super(element, referencedElement);
  }

  public abstract String getDescription();
}
