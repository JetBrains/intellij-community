// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyComprehensionElement;

/**
 * Comprehension-like element base, for list comps ang generators.
 */
public abstract class PyComprehensionElementImpl extends PyElementImpl implements PyComprehensionElement {
  public PyComprehensionElementImpl(ASTNode astNode) {
    super(astNode);
  }
}
