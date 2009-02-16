/*
 * User: anna
 * Date: 16-Feb-2009
 */
package com.intellij.refactoring.extractMethodObject;

import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

public class MethodToMoveUsageInfo extends UsageInfo {
  private boolean myMove = true;

  public MethodToMoveUsageInfo(@NotNull PsiMethod element) {
    super(element);
  }

  public boolean isMove() {
    return myMove;
  }

  public void setMove(boolean move) {
    myMove = move;
  }
}