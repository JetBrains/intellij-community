/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 03.08.2006
 * Time: 17:44:12
 */
package com.intellij.refactoring.move;

import org.jetbrains.annotations.Nullable;
import com.intellij.refactoring.MoveDestination;

public interface MoveClassesOrPackagesCallback extends MoveCallback {

  void classesOrPackagesMoved(MoveDestination destination);

  /**
   * Allows to override the name of the elements to move which is displayed in the dialog.
   *
   * @return the text that should be displayed, or null if the default text should be displayed.
   */
  @Nullable
  String getElementsToMoveName();
}