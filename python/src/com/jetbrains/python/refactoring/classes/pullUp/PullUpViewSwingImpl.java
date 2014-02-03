/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.google.common.base.Preconditions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.ui.PyClassCellRenderer;
import com.jetbrains.python.refactoring.classes.ui.PyMemberSelectionPanel;
import com.jetbrains.python.refactoring.classes.ui.PyMemberSelectionTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collection;
import java.util.List;


//TODO: Merge with UpDirectedMembersMovingDialog and its children

/**
 * @author Ilya.Kazakevich
 * Pull up view implementation
 */
class PullUpViewSwingImpl extends DialogWrapper implements PyPullUpView {
  @NotNull
  private final PyPullUpPresenter myPresenter;
  @NotNull
  private final DefaultComboBoxModel myParentsmodel;
  @NotNull
  private final JPanel myTopPanel;
  @NotNull
  private final JComponent myCenterPanel;
  @NotNull
  private final PyMemberSelectionTable myMembersPanelTable;
  @NotNull
  private final Project myProject;

  /**
   * @param project project where refactoring takes place
   * @param presenter presenter for this view
   * @param clazz class to refactor
   */
  PullUpViewSwingImpl(@NotNull Project project, @NotNull final PyPullUpPresenter presenter, @NotNull PyClass clazz) {
    super(project);
    this.myProject = project;
    setTitle(PyPullUpHandler.REFACTORING_NAME);
    myPresenter = presenter;

    myParentsmodel = new DefaultComboBoxModel();

    ComboBox parentsCombo = new ComboBox(myParentsmodel);
    parentsCombo.setRenderer(new PyClassCellRenderer());

    JLabel mainLabel = new JLabel();
    mainLabel.setText(RefactoringBundle.message("pull.up.members.to", PyClassCellRenderer.getClassText(clazz)));
    mainLabel.setLabelFor(parentsCombo);


    myTopPanel = new JPanel();
    myTopPanel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.anchor = GridBagConstraints.WEST;
    myTopPanel.add(mainLabel, gbConstraints);
    myTopPanel.add(mainLabel, gbConstraints);
    gbConstraints.gridy++;
    myTopPanel.add(parentsCombo, gbConstraints);


    myCenterPanel = new JPanel(new BorderLayout());
    PyMemberSelectionPanel membersPanel = new PyMemberSelectionPanel(RefactoringBundle.message("members.to.be.pulled.up"));
    myMembersPanelTable = membersPanel.getTable();
    gbConstraints.gridy++;
    myCenterPanel.add(membersPanel, BorderLayout.CENTER);

    parentsCombo.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          myMembersPanelTable.fireExternalDataChange();
        }
      }
    });
  }

  @NotNull
  @Override
  protected JComponent createNorthPanel() {
    return myTopPanel;
  }

  @NotNull
  protected String getHelpId() {
    return "python.reference.pullMembersUp";
  }

  protected JComponent createCenterPanel() {
    return myCenterPanel;
  }

  @Override
  protected void doOKAction() {
    myPresenter.okClicked();
  }

  @NotNull
  @Override
  public Collection<PyMemberInfo> getSelectedMemberInfos() {
    return myMembersPanelTable.getSelectedMemberInfos();
  }

  @Override
  public boolean showConflictsDialog(@NotNull MultiMap<PsiElement, String> conflicts) {
    Preconditions.checkArgument(!conflicts.isEmpty(), "Can't show dialog for empty conflicts");
    ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts);
    conflictsDialog.show();
    return conflictsDialog.isOK();
  }

  @Override
  public void closeDialog() {
    close(OK_EXIT_CODE);
  }

  @NotNull
  @Override
  public PyClass getSelectedParent() {
    return (PyClass)myParentsmodel.getSelectedItem();
  }

  @Override
  public void init(@NotNull Collection<PyClass> parents,
                   @NotNull MemberInfoModel<PyElement, PyMemberInfo> memberInfoModel,
                   @NotNull List<PyMemberInfo> members) {
    Preconditions.checkState(!isVisible(), "Already initialzed");
    for (PyClass parent : parents) {
      myParentsmodel.addElement(parent);
    }

    myMembersPanelTable.setMemberInfoModel(memberInfoModel);
    myMembersPanelTable.addMemberInfoChangeListener(memberInfoModel);
    myMembersPanelTable.setMemberInfos(members);
    init();
    show();
  }
}
