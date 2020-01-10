// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.sh.psi.ShFunctionName;
import com.intellij.sh.psi.ShFunctionNamedElement;
import com.intellij.sh.psi.manipulator.ShElementGenerator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ShFunctionNamedElementImpl extends ShCompositeElementImpl implements ShFunctionNamedElement {
  public ShFunctionNamedElementImpl(ASTNode node) {
    super(node);
  }

  @Override
  public String getName() {
    return getText();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    ASTNode oldNode = getNode();
    ShFunctionName newElement = ShElementGenerator.createFunctionName(getProject(), name);
    oldNode.getTreeParent().replaceChild(oldNode, newElement.getNode());
    return this;
  }
}
