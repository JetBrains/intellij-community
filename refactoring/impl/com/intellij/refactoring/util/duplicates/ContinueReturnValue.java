/*
 * User: anna
 * Date: 23-Oct-2008
 */
package com.intellij.refactoring.util.duplicates;

import org.jetbrains.annotations.NonNls;

public class ContinueReturnValue extends GotoReturnValue {
  public boolean isEquivalent(final ReturnValue other) {
    return other instanceof ContinueReturnValue;
  }


  @NonNls
  public String getGotoStatement() {
    return "if (a) continue;";
  }
}