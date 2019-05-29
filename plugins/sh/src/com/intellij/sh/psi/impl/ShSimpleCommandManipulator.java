// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.sh.psi.ShSimpleCommandElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShSimpleCommandManipulator extends AbstractElementManipulator<ShSimpleCommandElement> {
  @Nullable
  @Override
  public ShSimpleCommandElement handleContentChange(@NotNull ShSimpleCommandElement element,
                                                    @NotNull TextRange range,
                                                    String newContent) throws IncorrectOperationException {
    ASTNode oldNode = element.getNode();
    ShSimpleCommandElement newElement = ShElementGenerator.createSimpleCommand(element.getProject(), newContent);
    oldNode.getTreeParent().replaceChild(oldNode, newElement.getNode());
    return element;
  }
}
