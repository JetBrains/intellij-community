/*
 * @author max
 */
package com.jetbrains.python.psi;

import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.Stubbed;

public interface PyParameterStub extends NamedStub {
  @Stubbed
  boolean isPositionalContainer();

  @Stubbed boolean isKeywordContainer();
}