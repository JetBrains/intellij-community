// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.common.AbstractBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ShFormatterModelBuilder implements FormattingModelBuilder {
  @NotNull
  @Override
  public FormattingModel createModel(PsiElement element, CodeStyleSettings settings) {
    return createDumbModel(element);
  }

  @NotNull
  private static FormattingModel createDumbModel(@NotNull PsiElement element) {
    AbstractBlock block = new AbstractBlock(element.getNode(), null, null) {
      @Override
      protected List<Block> buildChildren() {
        return EMPTY;
      }

      @Nullable
      @Override
      public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
        return null;
      }

      @Override
      public boolean isLeaf() {
        return true;
      }
    };

    Document document = FormattingDocumentModelImpl.createOn(element.getContainingFile()).getDocument();
    FormattingDocumentModel model = new FormattingDocumentModel() {
      @Override
      public int getLineNumber(int offset) {
        return document.getLineNumber(offset);
      }

      @Override
      public int getLineStartOffset(int line) {
        return document.getLineStartOffset(line);
      }

      @NotNull
      @Override
      public CharSequence getText(TextRange textRange) {
        return document.getText(textRange);
      }

      @Override
      public int getTextLength() {
        return document.getTextLength();
      }

      @NotNull
      @Override
      public Document getDocument() {
        return document;
      }

      @Override
      public boolean containsWhiteSpaceSymbolsOnly(int startOffset, int endOffset) {
        return false;
      }

      @NotNull
      @Override
      public CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText,
                                                      int startOffset,
                                                      int endOffset,
                                                      ASTNode nodeAfter, boolean changedViaPsi) {
        return whiteSpaceText;
      }
    };

    return new FormattingModel() {
      @NotNull
      @Override
      public Block getRootBlock() {
        return block;
      }

      @NotNull
      @Override
      public FormattingDocumentModel getDocumentModel() {
        return model;
      }

      @Override
      public TextRange replaceWhiteSpace(TextRange textRange, String whiteSpace) {
        return textRange;
      }

      @Override
      public TextRange shiftIndentInsideRange(ASTNode node, TextRange range, int indent) {
        return range;
      }

      @Override
      public void commitChanges() {
      }
    };
  }
}
