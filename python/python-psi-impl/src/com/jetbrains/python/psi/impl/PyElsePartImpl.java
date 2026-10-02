// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElsePart;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class PyElsePartImpl extends PyElementImpl implements PyElsePart {

  public PyElsePartImpl(ASTNode astNode) {
    super(astNode);
  }
}
