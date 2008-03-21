package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyClass;

/**
 * @author yole
 */
public class PySuperClassIndex extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String, PyClass> KEY = new StubIndexKey<String, PyClass>("Py.class.super");

  public StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }
}
