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
package com.jetbrains.python.refactoring.classes.pullUp;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.AbstractUsesDependencyMemberInfoModel;
import com.intellij.refactoring.classMembers.DependencyMemberInfoModel;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.ui.PyClassCellRenderer;
import com.jetbrains.python.refactoring.classes.ui.UpDirectedMembersMovingDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

/**
 * @author Dennis.Ushakov
 */
public class PyPullUpDialog extends UpDirectedMembersMovingDialog {
  private JComboBox myClassCombo;
  private Collection<PyClass> mySuperClasses;
  private final PyMemberInfoStorage myStorage;

  public PyPullUpDialog(final Project project, final PyClass clazz, final Collection<PyClass> superClasses, final PyMemberInfoStorage storage) {
    super(project, clazz);
    myStorage = storage;
    mySuperClasses = new TreeSet<PyClass>(new Comparator<PyClass>() {
      public int compare(final PyClass o1, final PyClass o2) {
        final String name1 = PyClassCellRenderer.getClassText(o1);
        final String name2 = PyClassCellRenderer.getClassText(o2);
        return name1 == null ? -1 : name1.compareTo(name2);
      }
    });
    mySuperClasses = superClasses;
    myMemberInfos = myStorage.getClassMemberInfos(myClass);

    setTitle(PyPullUpHandler.REFACTORING_NAME);

    init();
  }

  protected DependencyMemberInfoModel<PyElement, PyMemberInfo> createMemberInfoModel() {
    return new MyMemberInfoModel(myClass);
  }

  protected String getHelpId() {
    return "python.reference.pullMembersUp";
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.anchor = GridBagConstraints.WEST;
    final JLabel classComboLabel = new JLabel();
    panel.add(classComboLabel, gbConstraints);

    myClassCombo = new JComboBox(mySuperClasses.toArray());
    myClassCombo.setRenderer(new PyClassCellRenderer());
    final String fqn = PyClassCellRenderer.getClassText(myClass);
    classComboLabel.setText(RefactoringBundle.message("pull.up.members.to", fqn));
    classComboLabel.setLabelFor(myClassCombo);
    myClassCombo.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateMembersInfo();
          if (myMemberSelectionPanel != null) {
            ((MyMemberInfoModel)myMemberInfoModel).setSuperClass(getSuperClass());
            myMemberSelectionPanel.getTable().setMemberInfos(myMemberInfos);
            myMemberSelectionPanel.getTable().fireExternalDataChange();
          }
        }
      }
    });
    gbConstraints.gridy++;
    panel.add(myClassCombo, gbConstraints);
    updateMembersInfo();

    return panel;
  }

  private void updateMembersInfo() {
    final PyClass targetClass = (PyClass)myClassCombo.getSelectedItem();
    myMemberInfos = myStorage.getIntermediateMemberInfosList(targetClass);
  }

  @Override
  public boolean checkConflicts() {
    final Collection<PyMemberInfo> infos = getSelectedMemberInfos();
    PyClass superClass = getSuperClass();
    if (!checkWritable(superClass, infos)) return false;
    MultiMap<PsiElement,String> conflicts = PullUpConflictsUtil.checkConflicts(infos, superClass);
    if (!conflicts.isEmpty()) {
      ConflictsDialog conflictsDialog = new ConflictsDialog(myClass.getProject(), conflicts);
      conflictsDialog.show();
      final boolean ok = conflictsDialog.isOK();
      if (!ok && conflictsDialog.isShowConflicts()) close(CANCEL_EXIT_CODE);
      return ok;
    }
    return true;
  }

  @Nullable
  public PyClass getSuperClass() {
    return myClassCombo != null ? (PyClass)myClassCombo.getSelectedItem() : null;
  }

  protected String getMembersBorderTitle() {
    return RefactoringBundle.message("members.to.be.pulled.up");
  }

  private class MyMemberInfoModel extends AbstractUsesDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo> {
    public MyMemberInfoModel(PyClass clazz) {
      super(clazz, getSuperClass(), false);
    }


    public boolean isMemberEnabled(PyMemberInfo member) {
      PyClass currentSuperClass = getSuperClass();
      return (currentSuperClass == null ||
             !myStorage.getDuplicatedMemberInfos(currentSuperClass).contains(member)) &&
             member.getMember() != currentSuperClass;
    }

    public boolean isAbstractEnabled(PyMemberInfo member) {
      return false;
    }

    public int checkForProblems(@NotNull PyMemberInfo member) {
      return member.isChecked() ? OK : super.checkForProblems(member);
    }

    @Override
    protected int doCheck(@NotNull PyMemberInfo memberInfo, int problem) {
      if (problem == ERROR && memberInfo.isStatic()) {
        return WARNING;
      }
      return problem;
    }
  }
}
