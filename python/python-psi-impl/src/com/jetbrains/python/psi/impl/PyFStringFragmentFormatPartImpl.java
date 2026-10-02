// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyFStringFragmentFormatPart;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class PyFStringFragmentFormatPartImpl extends PyElementImpl implements PyFStringFragmentFormatPart {
  public PyFStringFragmentFormatPartImpl(ASTNode astNode) {
    super(astNode);
  }
}
