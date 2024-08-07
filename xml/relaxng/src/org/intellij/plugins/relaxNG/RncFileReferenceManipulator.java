// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.relaxNG;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.psi.RncFileReference;
import org.intellij.plugins.relaxNG.compact.psi.util.RenameUtil;
import org.jetbrains.annotations.NotNull;

public class RncFileReferenceManipulator extends AbstractElementManipulator<RncFileReference> {
  @Override
  public RncFileReference handleContentChange(@NotNull RncFileReference element, @NotNull TextRange range, String newContent) throws
                                                                                                            IncorrectOperationException {
    final ASTNode node = element.getNode();
    assert node != null;

    final ASTNode literal = node.findChildByType(RncTokenTypes.LITERAL);
    if (literal != null) {
      assert range.equals(element.getReferenceRange());
      final PsiManager manager = element.getManager();
      final ASTNode newChild = RenameUtil.createLiteralNode(manager, newContent);
      literal.getTreeParent().replaceChild(literal, newChild);
    }
    return element;
  }

  @Override
  public @NotNull TextRange getRangeInElement(@NotNull RncFileReference element) {
    return element.getReferenceRange();
  }
}
