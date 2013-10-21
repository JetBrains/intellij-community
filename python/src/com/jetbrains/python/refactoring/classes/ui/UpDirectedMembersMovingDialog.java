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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.classMembers.DependencyMemberInfoModel;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public abstract class UpDirectedMembersMovingDialog extends DialogWrapper {
  protected DependencyMemberInfoModel<PyElement, PyMemberInfo> myMemberInfoModel;
  protected PyMemberSelectionPanel myMemberSelectionPanel;
  protected PyClass myClass;
  protected List<PyMemberInfo> myMemberInfos;

  public UpDirectedMembersMovingDialog(Project project, final PyClass clazz) {
    super(project, true);
    myClass = clazz;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    myMemberSelectionPanel = new PyMemberSelectionPanel(getMembersBorderTitle(), myMemberInfos, null);
    myMemberInfoModel = createMemberInfoModel();
    myMemberInfoModel.memberInfoChanged(new MemberInfoChange<PyElement, PyMemberInfo>(myMemberInfos));
    myMemberSelectionPanel.getTable().setMemberInfoModel(myMemberInfoModel);
    myMemberSelectionPanel.getTable().addMemberInfoChangeListener(myMemberInfoModel);
    panel.add(myMemberSelectionPanel, BorderLayout.CENTER);

    return panel;
  }

  protected abstract String getMembersBorderTitle();

  protected abstract DependencyMemberInfoModel<PyElement, PyMemberInfo> createMemberInfoModel();

  protected void doOKAction() {
    if(!checkConflicts()) return;
    close(OK_EXIT_CODE);
  }

  public boolean isOKActionEnabled() {
    return getSelectedMemberInfos().size() > 0 && super.isOKActionEnabled();
  }

  public abstract boolean checkConflicts();

  protected static boolean checkWritable(final PyClass superClass, final Collection<PyMemberInfo> infos) {
    if (infos.size() ==0) {
      return true;
    }
    final PyElement element = infos.iterator().next().getMember();
    final Project project = element.getProject();
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, superClass)) return false;
    final PyClass container = PyUtil.getContainingClassOrSelf(element);
    if (container == null || !CommonRefactoringUtil.checkReadOnlyStatus(project, container)) return false;
    for (PyMemberInfo info : infos) {
      final PyElement member = info.getMember();
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, member)) return false;
    }
    return true;
  }

  public Collection<PyMemberInfo> getSelectedMemberInfos() {
    ArrayList<PyMemberInfo> list = new ArrayList<PyMemberInfo>(myMemberInfos.size());
    for (PyMemberInfo info : myMemberInfos) {
      if (info.isChecked() && myMemberInfoModel.isMemberEnabled(info)) {
        list.add(info);
      }
    }
    return list;
  }
}
