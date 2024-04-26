// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;


public final class PySuperClassIndex extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String, PyClass> KEY = StubIndexKey.createIndexKey("Py.class.super");

  @Override
  @NotNull
  public StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }
}
