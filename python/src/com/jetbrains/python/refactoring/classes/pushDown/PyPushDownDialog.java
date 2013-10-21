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
package com.jetbrains.python.refactoring.classes.pushDown;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.classMembers.UsedByDependencyMemberInfoModel;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.ui.PyMemberSelectionPanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class PyPushDownDialog extends RefactoringDialog {
  private final List<PyMemberInfo> myMemberInfos;
  private final PyClass myClass;
  private MemberInfoModel<PyElement, PyMemberInfo> myMemberInfoModel;

  public PyPushDownDialog(Project project, PyClass aClass, PyMemberInfoStorage memberInfos) {
    super(project, true);
    myMemberInfos = memberInfos.getClassMemberInfos(aClass);
    myClass = aClass;

    setTitle(PyPushDownHandler.REFACTORING_NAME);

    init();
  }

  protected String getHelpId() {
    return "python.reference.pushMembersDown";
  }

  @Override
  protected void doAction() {
    if(!isOKActionEnabled()) return;

    final PyPushDownProcessor processor = new PyPushDownProcessor(getProject(), myClass, getSelectedMemberInfos());
    invokeRefactoring(processor);
  }

  protected JComponent createNorthPanel() {
    GridBagConstraints gbConstraints = new GridBagConstraints();

    JPanel panel = new JPanel(new GridBagLayout());

    gbConstraints.insets = new Insets(4, 0, 4, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.anchor = GridBagConstraints.WEST;
    final String fqn = myClass.getName();
    panel.add(new JLabel(RefactoringBundle.message("push.members.from.0.down.label", fqn)), gbConstraints);
    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    final PyMemberSelectionPanel memberSelectionPanel = new PyMemberSelectionPanel(
      RefactoringBundle.message("members.to.be.pushed.down.panel.title"),
      myMemberInfos, null);
    panel.add(memberSelectionPanel, BorderLayout.CENTER);

    myMemberInfoModel = new UsedByDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo>(myClass);
    myMemberInfoModel.memberInfoChanged(new MemberInfoChange<PyElement, PyMemberInfo>(myMemberInfos));
    memberSelectionPanel.getTable().setMemberInfoModel(myMemberInfoModel);
    memberSelectionPanel.getTable().addMemberInfoChangeListener(myMemberInfoModel);

    return panel;
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
