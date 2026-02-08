// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.ui;

import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.ui.AbstractMemberSelectionTable;
import com.intellij.ui.icons.RowIcon;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class PyMemberSelectionTable extends AbstractMemberSelectionTable<PyElement, PyMemberInfo<PyElement>> {

  private final boolean mySupportAbstract;

  public PyMemberSelectionTable(
    final @NotNull List<PyMemberInfo<PyElement>> memberInfos,
    final @Nullable MemberInfoModel<PyElement, PyMemberInfo<PyElement>> model,
    final boolean supportAbstract) {
    super(memberInfos, model, (supportAbstract ? getAbstractTitle() : null));
    mySupportAbstract = supportAbstract;
  }

  @Override
  protected @Nullable Object getAbstractColumnValue(final PyMemberInfo<PyElement> memberInfo) {
    //TODO: Too many logic, move to presenters
    return (mySupportAbstract && memberInfo.isChecked() && myMemberInfoModel.isAbstractEnabled(memberInfo)) ? memberInfo.isToAbstract() : null;
  }

  @Override
  protected boolean isAbstractColumnEditable(final int rowIndex) {
    return mySupportAbstract && myMemberInfoModel.isAbstractEnabled(myMemberInfos.get(rowIndex));
  }

  @Override
  protected void setVisibilityIcon(PyMemberInfo<PyElement> memberInfo, RowIcon icon) {
  }

  @Override
  protected Icon getOverrideIcon(PyMemberInfo<PyElement> memberInfo) {
    final PsiElement member = memberInfo.getMember();
    Icon overrideIcon = EMPTY_OVERRIDE_ICON;
    if (member instanceof PyFunction && memberInfo.getOverrides() != null && memberInfo.getOverrides()) {
      overrideIcon = AllIcons.General.OverridingMethod;
    }
    return overrideIcon;
  }

  private static @Nls String getAbstractTitle() {
    return RefactoringBundle.message("make.abstract");
  }
}
