/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.05.2002
 * Time: 15:01:44
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.psi.*;

/**
 *  Parameters used in expression
 */
class ParameterInExprUsageInfo extends InExprUsageInfo {
  ParameterInExprUsageInfo(PsiElement elem) {
    super(elem);
  }

}
