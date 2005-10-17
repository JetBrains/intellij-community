/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 07.05.2002
 * Time: 11:26:45
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;

/**
 * Usage of an expression in method
 */
public class InternalUsageInfo extends UsageInfo {
  InternalUsageInfo(PsiElement e) {
    super(e);
  }
}
