// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.psi.stubs.PyStarImportElementStub;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class PyStarImportElementStubImpl extends PyStubBase<PyStarImportElement> implements PyStarImportElementStub {
  protected PyStarImportElementStubImpl(final StubElement parent) {
    super(parent, PyElementTypes.STAR_IMPORT_ELEMENT);
  }
}
