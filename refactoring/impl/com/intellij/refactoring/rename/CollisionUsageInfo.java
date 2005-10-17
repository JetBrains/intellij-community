/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 05.06.2002
 * Time: 12:25:03
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.rename;

import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.psi.PsiElement;

public class CollisionUsageInfo extends MoveRenameUsageInfo {
  public CollisionUsageInfo(PsiElement element, PsiElement referencedElement) {
    super(element, null, referencedElement);
  }
}
