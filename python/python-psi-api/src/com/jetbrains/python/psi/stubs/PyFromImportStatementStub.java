// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.Nullable;


public interface PyFromImportStatementStub extends StubElement<PyFromImportStatement> {
  @Nullable
  QualifiedName getImportSourceQName();

  /**
   * @deprecated Use stub-based {@link PyFromImportStatement#getStarImportElement()} instead.
   */
  @Deprecated(forRemoval = true)
  boolean isStarImport();

  int getRelativeLevel();
}
