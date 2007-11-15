/*
 * @author max
 */
package com.intellij.lang.properties;

import com.intellij.lang.refactoring.DefaultRefactoringSupportProvider;
import com.intellij.psi.PsiElement;

public class PropertiesRefactoringSupportProvider extends DefaultRefactoringSupportProvider {
  public boolean isSafeDeleteAvailable(PsiElement element) {
    return true;
  }
}