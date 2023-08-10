// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyAnnotationStub;
import org.jetbrains.annotations.Nullable;


public interface PyAnnotation extends PyElement, StubBasedPsiElement<PyAnnotationStub> {
  @Nullable
  PyExpression getValue();
}
