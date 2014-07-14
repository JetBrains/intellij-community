package com.jetbrains.python.refactoring.classes.membersManager.vp;

import org.jetbrains.annotations.NotNull;

/**
 * To be thrown when {@link com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedViewSwingImpl} or its children
 * assumes that data entered by user in {@link com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedView} is invalid.
 * See {@link MembersBasedPresenterImpl#validateView()} for info why exception user
 * @author Ilya.Kazakevich
 */
public class BadDataException extends Exception {
  /**
   * @param message what exactly is wrong with data
   */
  public BadDataException(@NotNull final String message) {
    super(message);
  }
}
