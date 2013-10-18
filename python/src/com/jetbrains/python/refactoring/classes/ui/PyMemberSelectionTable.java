package com.jetbrains.python.refactoring.classes.ui;

import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.ui.AbstractMemberSelectionTable;
import com.intellij.ui.RowIcon;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;

import javax.swing.*;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class PyMemberSelectionTable extends AbstractMemberSelectionTable<PyElement, PyMemberInfo> {
  public PyMemberSelectionTable(final List<PyMemberInfo> memberInfos,
                                  final MemberInfoModel<PyElement, PyMemberInfo> model) {
    super(memberInfos, model, null);
  }

  protected Object getAbstractColumnValue(PyMemberInfo memberInfo) {
    return null;
  }

  protected boolean isAbstractColumnEditable(int rowIndex) {
    return false;
  }

  protected void setVisibilityIcon(PyMemberInfo memberInfo, RowIcon icon) {}

  protected Icon getOverrideIcon(PyMemberInfo memberInfo) {
    final PsiElement member = memberInfo.getMember();
    Icon overrideIcon = EMPTY_OVERRIDE_ICON;
    if (member instanceof PyFunction && memberInfo.getOverrides() != null && memberInfo.getOverrides()) {
      overrideIcon = AllIcons.General.OverridingMethod;
    }
    return overrideIcon;
  }
}
