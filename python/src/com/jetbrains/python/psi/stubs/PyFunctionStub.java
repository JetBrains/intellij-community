/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

public interface PyFunctionStub extends NamedStub<PyFunction> {
  @NotNull
  PyParameterListStub getParameterList();
}