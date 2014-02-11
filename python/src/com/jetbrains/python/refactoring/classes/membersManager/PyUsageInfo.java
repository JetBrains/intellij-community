package com.jetbrains.python.refactoring.classes.membersManager;

import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

/**
 * TODO: Make it generic to allow to reuse in another projects?
 * Usage info that displays destination (where should member be moved)
 *
 * @author Ilya.Kazakevich
 */
class PyUsageInfo extends UsageInfo {
  @NotNull
  private final PyClass myTo;

  PyUsageInfo(@NotNull final PyClass to) {
    super(to, true); //TODO: Make super generic and get rid of field?
    myTo = to;
  }

  @NotNull
  public PyClass getTo() {
    return myTo;
  }
}
