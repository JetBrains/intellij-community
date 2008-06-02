/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyClass;

public class PyClassNameIndex extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String,PyClass> KEY = new StubIndexKey<String, PyClass>("Py.class.shortName", 987984651321965798L);

  public StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }
}