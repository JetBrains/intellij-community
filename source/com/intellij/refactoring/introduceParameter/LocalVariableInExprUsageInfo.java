/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.05.2002
 * Time: 15:08:11
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.psi.*;

class LocalVariableInExprUsageInfo extends InExprUsageInfo {
  LocalVariableInExprUsageInfo(PsiElement elem) {
    super(elem);
  }
}
