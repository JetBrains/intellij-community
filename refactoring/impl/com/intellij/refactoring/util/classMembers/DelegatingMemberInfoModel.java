/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 09.07.2002
 * Time: 15:44:58
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util.classMembers;

import org.jetbrains.annotations.NotNull;

public class DelegatingMemberInfoModel implements MemberInfoModel {
  private MemberInfoModel myDelegatingTarget;

  public DelegatingMemberInfoModel(MemberInfoModel delegatingTarget) {
    myDelegatingTarget = delegatingTarget;
  }

  protected DelegatingMemberInfoModel() {

  }

  public MemberInfoModel getDelegatingTarget() {
    return myDelegatingTarget;
  }

  public boolean isMemberEnabled(MemberInfo member) {
    return myDelegatingTarget.isMemberEnabled(member);
  }

  public boolean isCheckedWhenDisabled(MemberInfo member) {
    return myDelegatingTarget.isCheckedWhenDisabled(member);
  }

  public boolean isAbstractEnabled(MemberInfo member) {
    return myDelegatingTarget.isAbstractEnabled(member);
  }

  public boolean isAbstractWhenDisabled(MemberInfo member) {
    return myDelegatingTarget.isAbstractWhenDisabled(member);
  }

  public int checkForProblems(@NotNull MemberInfo member) {
    return myDelegatingTarget.checkForProblems(member);
  }

  public void memberInfoChanged(MemberInfoChange event) {
    myDelegatingTarget.memberInfoChanged(event);
  }

  public Boolean isFixedAbstract(MemberInfo member) {
    return myDelegatingTarget.isFixedAbstract(member);
  }

  public String getTooltipText(MemberInfo member) {
    return myDelegatingTarget.getTooltipText(member);
  }
}
