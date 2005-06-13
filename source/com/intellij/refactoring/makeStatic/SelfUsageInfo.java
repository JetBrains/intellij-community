/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 09.07.2002
 * Time: 12:39:20
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.makeStatic;

import com.intellij.psi.*;

public class SelfUsageInfo extends InternalUsageInfo {
  SelfUsageInfo(PsiElement element, PsiElement referencedElement) {
    super(element, referencedElement);
  }
}
