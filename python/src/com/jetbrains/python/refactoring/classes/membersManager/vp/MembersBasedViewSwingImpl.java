// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.membersManager.vp;

import com.google.common.base.Preconditions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts.BorderTitle;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.MembersConflictDialog;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.ui.PyMemberSelectionPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.Collection;

/**
 * {@link MembersBasedView} implementation on swing.
 * Consists of {@link #myTopPanel} and {@link #myCenterPanel}. Children must fill them in constructor.
 * Presenter is stored in {@link #myPresenter}.
 * Panel with members in {@link #myPyMemberSelectionPanel}
 *
 * @param <P> View presenter class
 * @param <C> View configuration class
 */
public abstract class MembersBasedViewSwingImpl<P extends MembersBasedPresenter, C extends MembersViewInitializationInfo>
  extends RefactoringDialog implements MembersBasedView<C> {

  /**
   * Panel to be displayed on the top
   */
  protected final @NotNull JPanel myTopPanel;
  /**
   * Panel to be displayed at the center
   */
  protected final @NotNull JComponent myCenterPanel;

  /**
   * Presenter
   */
  protected final @NotNull P myPresenter;

  /**
   * Panel with members
   */
  protected final @NotNull PyMemberSelectionPanel myPyMemberSelectionPanel;

  private boolean myConfigured;


  /**
   *
   * @param project         project this view runs
   * @param presenter       view's presenter
   * @param title           window title
   * @param supportAbstract supports "abstract" column?
   */
  protected MembersBasedViewSwingImpl(final @NotNull Project project, final @NotNull P presenter, final @NotNull @BorderTitle String title,
                                      final boolean supportAbstract) {
    super(project, true);
    myTopPanel = new JPanel(new BorderLayout());
    myCenterPanel = new JPanel(new BorderLayout());
    myPresenter = presenter;
    myPyMemberSelectionPanel = new PyMemberSelectionPanel(title, supportAbstract);
    //TODO: Take this from presenter to prevent inconsistence: now it is possible to create view that supports abstract backed by presenter that does not. And vice versa.
  }


  @Override
  public boolean showConflictsDialog(final @NotNull MultiMap<PyClass, PyMemberInfo<?>> duplicatesConflict,
                                     final @NotNull Collection<PyMemberInfo<?>> dependenciesConflicts) {
    Preconditions
      .checkArgument(!(duplicatesConflict.isEmpty() && dependenciesConflicts.isEmpty()), "Can't show dialog for empty conflicts");
    final DialogWrapper conflictsDialog = new MembersConflictDialog(myProject, duplicatesConflict, dependenciesConflicts);
    return conflictsDialog.showAndGet();
  }

  @Override
  public void showError(final @NotNull @DialogMessage String message) {
    Messages.showErrorDialog(getContentPane(), message);
  }

  @Override
  protected boolean hasPreviewButton() {
    return myPresenter.showPreview();
  }

  @Override
  protected void doAction() {
    myPresenter.okClicked();
  }

  @Override
  protected @NotNull JComponent createNorthPanel() {
    return myTopPanel;
  }

  @Override
  public void close() {
    close(OK_EXIT_CODE);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myCenterPanel;
  }

  @Override
  public @NotNull Collection<PyMemberInfo<PyElement>> getSelectedMemberInfos() {
    return myPyMemberSelectionPanel.getSelectedMemberInfos();
  }

  @Override
  public void invokeRefactoring(final @NotNull BaseRefactoringProcessor processor) {
    super.invokeRefactoring(processor);
  }

  @Override
  public void configure(final @NotNull C configInfo) {
    Preconditions.checkArgument(!myConfigured, "Already configured");
    myConfigured = true;
    myPyMemberSelectionPanel.init(configInfo.getMemberInfoModel(), configInfo.getMemberInfos());
  }

  @Override
  public void initAndShow() {
    Preconditions.checkArgument(myConfigured, "Not configured, run 'configure' first!");
    init();
    myPyMemberSelectionPanel.redraw();  // To display errors for checked member
    show();
  }
}