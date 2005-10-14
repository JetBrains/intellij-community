/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.05.2002
 * Time: 15:07:17
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.psi.*;

class ClassMemberInExprUsageInfo extends InExprUsageInfo {
  ClassMemberInExprUsageInfo(PsiElement elem) {
    super(elem);
  }
}
