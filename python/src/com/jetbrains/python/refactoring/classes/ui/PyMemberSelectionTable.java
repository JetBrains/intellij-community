/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
