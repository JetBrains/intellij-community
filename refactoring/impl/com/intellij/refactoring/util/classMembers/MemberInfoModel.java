/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 09.07.2002
 * Time: 14:58:46
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util.classMembers;

import org.jetbrains.annotations.NotNull;


public interface MemberInfoModel extends MemberInfoChangeListener {
  int OK = 0;
  int WARNING = 1;
  int ERROR = 2;

  boolean isMemberEnabled(MemberInfo member);

  boolean isCheckedWhenDisabled(MemberInfo member);

  boolean isAbstractEnabled(MemberInfo member);

  boolean isAbstractWhenDisabled(MemberInfo member);

  /**
   * Returns state of abstract checkbox for particular abstract member.
   * @param member MemberInfo for an ABSTRACT member
   * @return TRUE if fixed and true, FALSE if fixed and false, null if dont care
   */
  Boolean isFixedAbstract(MemberInfo member);

  int checkForProblems(@NotNull MemberInfo member);

  String getTooltipText(MemberInfo member);
}
