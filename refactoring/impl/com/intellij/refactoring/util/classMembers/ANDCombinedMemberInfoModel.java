/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 09.07.2002
 * Time: 15:20:25
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util.classMembers;

import org.jetbrains.annotations.NotNull;

public class ANDCombinedMemberInfoModel implements MemberInfoModel {
  private final MemberInfoModel myModel1;
  private final MemberInfoModel myModel2;
  private final MemberInfoTooltipManager myTooltipManager = new MemberInfoTooltipManager(new MemberInfoTooltipManager.TooltipProvider() {
    public String getTooltip(MemberInfo memberInfo) {
      final String tooltipText1 = myModel1.getTooltipText(memberInfo);
      if (tooltipText1 != null) return tooltipText1;
      return myModel2.getTooltipText(memberInfo);
    }
  });


  public ANDCombinedMemberInfoModel(MemberInfoModel model1, MemberInfoModel model2) {
    myModel1 = model1;
    myModel2 = model2;
  }

  public boolean isMemberEnabled(MemberInfo member) {
    return myModel1.isMemberEnabled(member) && myModel2.isMemberEnabled(member);
  }

  public boolean isCheckedWhenDisabled(MemberInfo member) {
    return myModel1.isCheckedWhenDisabled(member) && myModel2.isCheckedWhenDisabled(member);
  }

  public boolean isAbstractEnabled(MemberInfo member) {
    return myModel1.isAbstractEnabled(member) && myModel2.isAbstractEnabled(member);
  }

  public boolean isAbstractWhenDisabled(MemberInfo member) {
    return myModel1.isAbstractWhenDisabled(member) && myModel2.isAbstractWhenDisabled(member);
  }

  public int checkForProblems(@NotNull MemberInfo member) {
    return Math.max(myModel1.checkForProblems(member), myModel2.checkForProblems(member));
  }

  public void memberInfoChanged(MemberInfoChange event) {
    myTooltipManager.invalidate();
    myModel1.memberInfoChanged(event);
    myModel2.memberInfoChanged(event);
  }

  public Boolean isFixedAbstract(MemberInfo member) {
    final Boolean fixedAbstract1 = myModel1.isFixedAbstract(member);
    if(fixedAbstract1 == null) return null;
    if(fixedAbstract1.equals(myModel2.isFixedAbstract(member))) return fixedAbstract1;
    return null;
  }

  public MemberInfoModel getModel1() {
    return myModel1;
  }

  public MemberInfoModel getModel2() {
    return myModel2;
  }

  public String getTooltipText(MemberInfo member) {
    return myTooltipManager.getTooltip(member);
  }
}
