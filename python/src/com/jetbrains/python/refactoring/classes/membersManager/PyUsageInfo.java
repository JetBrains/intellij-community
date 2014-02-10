package com.jetbrains.python.refactoring.classes.membersManager;

import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

/**
 * TODO: Make it generic to allow to reuse in another projects?
 * TODO: Document
 *
 * @author Ilya.Kazakevich
 */
class PyUsageInfo extends UsageInfo {
  @NotNull
  private final PyClass myTo;

  PyUsageInfo(@NotNull PyClass to) {
    super(to, true); //TODO: Make super generic and get rid of field?
    this.myTo = to;
  }

  @NotNull
  public PyClass getTo() {
    return myTo;
  }
}
