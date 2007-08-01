/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 08.07.2002
 * Time: 17:55:02
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class UsesDependencyMemberInfoModel extends DependencyMemberInfoModel {
  private PsiClass myClass;

  public UsesDependencyMemberInfoModel(PsiClass aClass, PsiClass superClass, boolean recursive) {
    super(new UsesMemberDependencyGraph(aClass, superClass, recursive), ERROR);
    setTooltipProvider(new MemberInfoTooltipManager.TooltipProvider() {
      public String getTooltip(MemberInfo memberInfo) {
        return ((UsesMemberDependencyGraph) myMemberDependencyGraph).getElementTooltip(memberInfo.getMember());
      }
    });
    myClass = aClass;
  }

  public int checkForProblems(@NotNull MemberInfo memberInfo) {
    final int problem = super.checkForProblems(memberInfo);
    final PsiElement member = memberInfo.getMember();
    if(problem == ERROR
            && member instanceof PsiModifierListOwner
            && ((PsiModifierListOwner) member).hasModifierProperty(PsiModifier.STATIC)) {
      return WARNING;
    }
    return problem;
  }

  public void setSuperClass(PsiClass superClass) {
    setMemberDependencyGraph(new UsesMemberDependencyGraph(myClass, superClass, false));
  }

  public boolean isCheckedWhenDisabled(MemberInfo member) {
    return false;
  }

  public Boolean isFixedAbstract(MemberInfo member) {
    return null;
  }
}
