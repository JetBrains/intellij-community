package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.PsiMethod;

/**
 * @author dsl
 */
public interface InterfaceContainmentVerifier {
  boolean checkedInterfacesContain(PsiMethod psiMethod);
}
