package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;

/**
 * @author dsl
 */
public class UsedByDependencyMemberInfoModel extends DependencyMemberInfoModel {

  public UsedByDependencyMemberInfoModel(PsiClass aClass) {
    super(new UsedByMemberDependencyGraph(aClass), ERROR);
    setTooltipProvider(new MemberInfoTooltipManager.TooltipProvider() {
      public String getTooltip(MemberInfo memberInfo) {
        return ((UsedByMemberDependencyGraph) myMemberDependencyGraph).getElementTooltip(memberInfo.getMember());
      }
    });
  }

  public boolean isCheckedWhenDisabled(MemberInfo member) {
    return false;
  }

  public Boolean isFixedAbstract(MemberInfo member) {
    return null;
  }
}
