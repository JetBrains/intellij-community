// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.psi.impl.YAMLBlockScalarImpl;

public class YAMLFormattingModelBuilder implements FormattingModelBuilder {
  @NotNull
  @Override
  public FormattingModel createModel(PsiElement element, CodeStyleSettings settings) {
    PsiFile file = element.getContainingFile();
    Block rootBlock = createBlock(new YAMLFormattingContext(settings), element.getNode());
    return new DocumentBasedFormattingModel(rootBlock, settings, file);
  }

  @Nullable
  @Override
  public TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    return null;
  }

  @NotNull
  public static Block createBlock(@NotNull YAMLFormattingContext context,
                                  @NotNull ASTNode node) {
    IElementType nodeType = PsiUtilCore.getElementType(node);
    if (YAMLElementTypes.BLOCK_SCALAR_ITEMS.contains(nodeType)) {
      ASTNode blockScalarNode = node.getTreeParent();
      assert (blockScalarNode.getPsi() instanceof YAMLBlockScalarImpl);
      YAMLBlockScalarImpl blockScalarImpl = (YAMLBlockScalarImpl)blockScalarNode.getPsi();

      if (blockScalarImpl.getNthContentTypeChild(0) != node) {
        // node is not block scalar header
        return YAMLBlockScalarItemBlock.createBlockScalarItem(context, node);
      }
    }

    assert nodeType != YAMLElementTypes.SEQUENCE : "Sequence should be inlined!";
    assert nodeType != YAMLElementTypes.MAPPING : "Mapping should be inlined!";
    assert nodeType != YAMLElementTypes.DOCUMENT : "Document should be inlined!";

    return new YAMLFormattingBlock(context, node);
  }
}
