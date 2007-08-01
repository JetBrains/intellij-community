/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 09.07.2002
 * Time: 15:03:42
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public abstract class DependencyMemberInfoModel implements MemberInfoModel {
  protected MemberDependencyGraph myMemberDependencyGraph;
  private final int myProblemLevel;
  private MemberInfoTooltipManager myTooltipManager;

  public DependencyMemberInfoModel(MemberDependencyGraph memberDependencyGraph, int problemLevel) {
    myMemberDependencyGraph = memberDependencyGraph;
    myProblemLevel = problemLevel;
  }

  public void setTooltipProvider(MemberInfoTooltipManager.TooltipProvider tooltipProvider) {
    myTooltipManager = new MemberInfoTooltipManager(tooltipProvider);
  }

  public boolean isAbstractEnabled(MemberInfo member) {
    return true;
  }

  public boolean isAbstractWhenDisabled(MemberInfo member) {
    return false;
  }

  public boolean isMemberEnabled(MemberInfo member) {
    return true;
  }

  public int checkForProblems(@NotNull MemberInfo memberInfo) {
    if (memberInfo.isChecked()) return OK;
    final PsiElement member = memberInfo.getMember();

    if (myMemberDependencyGraph.getDependent().contains(member)) {
      return myProblemLevel;
    }
    return OK;
  }

  public void setMemberDependencyGraph(MemberDependencyGraph memberDependencyGraph) {
    myMemberDependencyGraph = memberDependencyGraph;
  }

  public void memberInfoChanged(MemberInfoChange event) {
    memberInfoChanged(event.getChangedMembers());
  }

  public void memberInfoChanged(final MemberInfo[] changedMembers) {
    if (myTooltipManager != null) myTooltipManager.invalidate();
    for (int i = 0; i < changedMembers.length; i++) {
      myMemberDependencyGraph.memberChanged(changedMembers[i]);
    }
  }

  public String getTooltipText(MemberInfo member) {
    if (myTooltipManager != null) {
      return myTooltipManager.getTooltip(member);
    } else {
      return null;
    }
  }
}
