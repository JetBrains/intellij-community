/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 30.05.2002
 * Time: 13:43:25
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.rename;


import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;

class FieldHidesOuterFieldUsageInfo extends ResolvableCollisionUsageInfo {
  public FieldHidesOuterFieldUsageInfo(PsiElement ref, PsiField field) {
    super(ref, field);
  }
}