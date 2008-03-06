/*
 * @author max
 */
package com.jetbrains.python.psi;

import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.Stubbed;
import org.jetbrains.annotations.NotNull;

public interface PyFunctionStub extends NamedStub {
  @NotNull
  @Stubbed
  PyParameterList getParameterList();
}