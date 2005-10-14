/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 04.06.2002
 * Time: 22:09:32
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.psi.*;

class ChangedMethodCallInfo extends InternalUsageInfo {
  ChangedMethodCallInfo(PsiElement e) {
    super(e);
  }
}
