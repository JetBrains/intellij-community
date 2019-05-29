// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.sh.psi.ShGenericCommandDirective;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShGenericCommandManipulator extends AbstractElementManipulator<ShGenericCommandDirective> {
  @Nullable
  @Override
  public ShGenericCommandDirective handleContentChange(@NotNull ShGenericCommandDirective element,
                                                       @NotNull TextRange range,
                                                       String newContent) throws IncorrectOperationException {
    ASTNode oldNode = element.getNode();
    ShGenericCommandDirective newElement = ShElementGenerator.createGenericCommand(element.getProject(), newContent);
    oldNode.getTreeParent().replaceChild(oldNode, newElement.getNode());
    return element;
  }
}
