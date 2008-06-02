/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyFunction;

public class PyFunctionNameIndex extends StringStubIndexExtension<PyFunction> {
  public static final StubIndexKey<String,PyFunction> KEY = new StubIndexKey<String, PyFunction>("Py.function.shortName", 987456513216489876L);

  public StubIndexKey<String, PyFunction> getKey() {
    return KEY;
  }
}