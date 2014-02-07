package com.jetbrains.python.refactoring.classes.membersManager.vp;

import com.jetbrains.python.vp.Presenter;

/**
 * Presenter for dialogs that display members
 *
 * @author Ilya.Kazakevich
 */
public interface MembersBasedPresenter extends Presenter {
  /**
   * User clicked on "ok" button
   */
  void okClicked();

  /**
   * @return true if dialog button "preview" should be displayed.
   * Preview uses {@link com.jetbrains.python.refactoring.classes.membersManager.PyMembersRefactoringBaseProcessor}
   */
  boolean showPreview();
}
