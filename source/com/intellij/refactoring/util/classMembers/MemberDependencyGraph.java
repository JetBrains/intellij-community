/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 08.07.2002
 * Time: 17:53:29
 * To change template for new interface use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;

import java.util.Set;

public interface MemberDependencyGraph {
  /**
   * Call this to notify that a new member info have been added
   * or a state of some memberInfo have been changed.
   * @param memberInfo
   */
  void memberChanged(MemberInfo memberInfo);

  /**
   * Returns class members that are dependent on checked MemberInfos.
   * @return set of PsiElements
   */
  Set getDependent();

  /**
   * Returns PsiElements of checked MemberInfos that element depends on.
   * element should belong to getDependent()
   * @param element
   * @return set of PsiElements
   */
  Set getDependenciesOf(PsiElement element);
}
