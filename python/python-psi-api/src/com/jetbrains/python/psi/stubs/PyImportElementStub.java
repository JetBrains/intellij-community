// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyImportElement;
import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.Nullable;


public interface PyImportElementStub extends StubElement<PyImportElement> {
  @Nullable
  QualifiedName getImportedQName();
  
  String getAsName();
}
