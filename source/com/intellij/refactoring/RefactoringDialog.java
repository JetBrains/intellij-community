/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Author: msk
 */
public abstract class RefactoringDialog extends DialogWrapper {

  /** The default exit code for "Refactor" action. */
  public static final int REFACTOR_EXIT_CODE = 3;

  /** The default exit code for "Preview" action. */
  public static final int PREVIEW_EXIT_CODE = 4;

  private Action myRefactorAction;
  private Action myPreviewAction;
  private boolean myCbPreviewResults;

  protected RefactoringDialog(Project project, boolean canBeParent) {
    super (project, canBeParent);
    myCbPreviewResults = true;
  }

  final public boolean isPreviewUsages() {
    return myCbPreviewResults;
  }

  protected void createDefaultActions() {
    super.createDefaultActions ();
    myRefactorAction = new RefactorAction();
    myPreviewAction = new PreviewAction();
  }

  /**
   * @return default implementation of "Refactor" action.
   */
  final protected Action getRefactorAction() {
    return myRefactorAction;
  }

  /**
   * @return default implementation of "Preview" action.
   */
  final protected Action getPreviewAction() {
    return myPreviewAction;
  }

  protected abstract void doAction();

  final protected void doPreviewAction () {
    validateButtons();
    if (!isOKActionEnabled()) return;
    myCbPreviewResults = true;
    doAction ();
  }

  final protected void doRefactorAction () {
    validateButtons();
    if (!isOKActionEnabled()) return;
    myCbPreviewResults = false;
    doAction ();
  }


  final protected void closeOKAction() { super.doOKAction(); }

  final protected void doOKAction() {
    validateButtons();

    if (!isOKActionEnabled()) return;
    doAction();

  }

  protected boolean areButtonsValid () { return true; }

  protected void validateButtons() {
    final boolean enabled = areButtonsValid ();
    getPreviewAction().setEnabled(enabled);
    getRefactorAction().setEnabled(enabled);
  }

  final protected boolean isActionsEnabled () {
    return getRefactorAction ().isEnabled () && getPreviewAction ().isEnabled ();
  }

  protected boolean hasHelpAction () {
    return true;
  }

  final protected Action[] createActions() {
    if (hasHelpAction ())
      return new Action[]{getRefactorAction(), getPreviewAction(), getCancelAction(), getHelpAction()};
    else
      return new Action[]{getRefactorAction(), getPreviewAction(), getCancelAction()};
  }

  private class RefactorAction extends AbstractAction {
    public RefactorAction() {
      putValue(Action.NAME, "Refactor");
      putValue(DEFAULT_ACTION, Boolean.TRUE);
    }

    public void actionPerformed(ActionEvent e) {
      doRefactorAction ();
    }
  }

  private class PreviewAction extends AbstractAction {
    public PreviewAction() {
      putValue(Action.NAME, "Preview");
    }

    public void actionPerformed(ActionEvent e) {
      doPreviewAction ();
    }
  }

}
