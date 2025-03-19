package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;

public final class PyExportedModuleAttributeIndex extends StringStubIndexExtension<PyElement> {
  public static final StubIndexKey<String, PyElement> KEY = StubIndexKey.createIndexKey("Py.module.attribute");

  @Override
  public @NotNull StubIndexKey<String, PyElement> getKey() {
    return KEY;
  }

  @Override
  public boolean traceKeyHashToVirtualFileMapping() {
    return true;
  }
}
