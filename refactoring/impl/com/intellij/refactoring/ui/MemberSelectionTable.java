/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 17.06.2002
 * Time: 16:35:43
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.ui;

import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.ui.RowIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.VisibilityIcons;

import javax.swing.*;
import java.util.List;

public class MemberSelectionTable extends AbstractMemberSelectionTable<PsiMember, MemberInfo> {

  public MemberSelectionTable(final List<MemberInfo> memberInfos, String abstractColumnHeader) {
    this(memberInfos, null, abstractColumnHeader);
  }

  public MemberSelectionTable(final List<MemberInfo> memberInfos, MemberInfoModel<PsiMember, MemberInfo> memberInfoModel, String abstractColumnHeader) {
    super(memberInfos, memberInfoModel, abstractColumnHeader);
  }

  @Override
  protected Object getAbstractColumnValue(MemberInfo memberInfo) {
    if (!(memberInfo.getMember() instanceof PsiMethod)) return null;
    if (memberInfo.isStatic()) return null;

    PsiMethod method = (PsiMethod)memberInfo.getMember();
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      final Boolean fixedAbstract = myMemberInfoModel.isFixedAbstract(memberInfo);
      if (fixedAbstract != null) return fixedAbstract;
    }

    if (!myMemberInfoModel.isAbstractEnabled(memberInfo)) {
      return myMemberInfoModel.isAbstractWhenDisabled(memberInfo);
    }
    else {
      return memberInfo.isToAbstract() ? Boolean.TRUE : Boolean.FALSE;
    }
  }

  @Override
  protected boolean isAbstractColumnEditable(int rowIndex) {
    MemberInfo info = myMemberInfos.get(rowIndex);
    if (!(info.getMember() instanceof PsiMethod)) return false;

    PsiMethod method = (PsiMethod)info.getMember();
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      if (myMemberInfoModel.isFixedAbstract(info) != null) {
        return false;
      }
    }

    return info.isChecked() && myMemberInfoModel.isAbstractEnabled(info);
  }


  @Override
  protected void setVisibilityIcon(MemberInfo memberInfo, RowIcon icon) {
    PsiMember member = memberInfo.getMember();
    PsiModifierList modifiers = member != null ? member.getModifierList() : null;
    if (modifiers != null) {
      VisibilityIcons.setVisibilityIcon(modifiers, icon);
    }
    else {
      icon.setIcon(IconUtil.getEmptyIcon(true), VISIBILITY_ICON_POSITION);
    }
  }

  @Override
  protected Icon getOverrideIcon(MemberInfo memberInfo) {
    PsiMember member = memberInfo.getMember();
    Icon overrideIcon = MemberSelectionTable.EMPTY_OVERRIDE_ICON;
    if (member instanceof PsiMethod) {
      if (Boolean.TRUE.equals(memberInfo.getOverrides())) {
        overrideIcon = MemberSelectionTable.OVERRIDING_METHOD_ICON;
      }
      else if (Boolean.FALSE.equals(memberInfo.getOverrides())) {
        overrideIcon = MemberSelectionTable.IMPLEMENTING_METHOD_ICON;
      }
      else {
        overrideIcon = MemberSelectionTable.EMPTY_OVERRIDE_ICON;
      }
    }
    return overrideIcon;
  }
}
