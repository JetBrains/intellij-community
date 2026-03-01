// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.pullUp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.refactoring.RefactoringBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedViewSwingImpl;
import com.jetbrains.python.refactoring.classes.ui.PyClassCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;


/**
 * @author Ilya.Kazakevich
 *         Pull up view implementation
 */
class PyPullUpViewSwingImpl extends MembersBasedViewSwingImpl<PyPullUpPresenter, PyPullUpViewInitializationInfo> implements PyPullUpView,
                                                                                                                            ItemListener {
  private final @NotNull ComboBox myParentsCombo;
  private final @NotNull DefaultComboBoxModel myParentsComboBoxModel;
  private final @NotNull PyPullUpNothingToRefactorMessage myNothingToRefactorMessage;

  /**
   * @param project                  project where refactoring takes place
   * @param presenter                presenter for this view
   * @param clazz                    class to refactor
   * @param nothingToRefactorMessage class that displays message "nothing to refactor" when presenter calls {@link #showNothingToRefactor()}
   */
  PyPullUpViewSwingImpl(final @NotNull Project project,
                        final @NotNull PyPullUpPresenter presenter,
                        final @NotNull PyClass clazz,
                        final @NotNull PyPullUpNothingToRefactorMessage nothingToRefactorMessage) {
    super(project, presenter, RefactoringBundle.message("members.to.be.pulled.up"), true);
    setTitle(PyPullUpHandler.getRefactoringName());
    myNothingToRefactorMessage = nothingToRefactorMessage;

    myParentsComboBoxModel = new DefaultComboBoxModel();

    myParentsCombo = new ComboBox(myParentsComboBoxModel);
    myParentsCombo.setRenderer(new PyClassCellRenderer());

    final JLabel mainLabel = new JLabel();
    mainLabel.setText(RefactoringBundle.message("pull.up.members.to", PyClassCellRenderer.getClassText(clazz)));
    mainLabel.setLabelFor(myParentsCombo);


    myTopPanel.setLayout(new GridBagLayout());
    final GridBagConstraints gbConstraints = new GridBagConstraints();

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
    myTopPanel.add(myParentsCombo, gbConstraints);

    gbConstraints.gridy++;
    myCenterPanel.add(myPyMemberSelectionPanel, BorderLayout.CENTER);
  }

  @Override
  protected @NotNull String getHelpId() {
    return "python.reference.pullMembersUp";
  }


  @Override
  public @NotNull PyClass getSelectedParent() {
    return (PyClass)myParentsComboBoxModel.getSelectedItem();
  }

  @Override
  public void showNothingToRefactor() {
    myNothingToRefactorMessage.showNothingToRefactor();
  }

  @Override
  public void configure(final @NotNull PyPullUpViewInitializationInfo configInfo) {
    super.configure(configInfo);
    for (final PyClass parent : configInfo.getParents()) {
      myParentsComboBoxModel.addElement(parent);
    }
    myPresenter.parentChanged();
    myParentsCombo.addItemListener(this);
  }

  @Override
  public void itemStateChanged(final ItemEvent e) {
    if (e.getStateChange() == ItemEvent.SELECTED) {
      myPyMemberSelectionPanel.redraw();
      myPresenter.parentChanged();
    }
  }
}
