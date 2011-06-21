package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.jetbrains.python.psi.PyFunction;

public interface PyFunctionStub extends NamedStub<PyFunction> {
  String getReturnTypeFromDocString();
  String getDeprecationMessage();
}