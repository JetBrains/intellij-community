// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.membersManager.vp;

import org.jetbrains.annotations.NotNull;

/**
 * To be thrown when {@link MembersBasedViewSwingImpl} or its children
 * assumes that data entered by user in {@link MembersBasedView} is invalid.
 * See {@link MembersBasedPresenterImpl#validateView()} for info why exception user
 * @author Ilya.Kazakevich
 */
public class BadDataException extends Exception {
  /**
   * @param message what exactly is wrong with data
   */
  public BadDataException(final @NotNull String message) {
    super(message);
  }
}
