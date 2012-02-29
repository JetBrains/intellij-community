package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PySuperClassIndex extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String, PyClass> KEY = StubIndexKey.createIndexKey("Py.class.super");

  @NotNull
  public StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }
}
