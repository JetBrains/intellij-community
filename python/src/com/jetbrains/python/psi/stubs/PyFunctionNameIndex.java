/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyFunction;

public class PyFunctionNameIndex extends StringStubIndexExtension<PyFunction> {
  public static final StubIndexKey<String,PyFunction> KEY = StubIndexKey.createIndexKey("Py.function.shortName");

  public StubIndexKey<String, PyFunction> getKey() {
    return KEY;
  }
}