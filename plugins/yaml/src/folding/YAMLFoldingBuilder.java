// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.CustomFoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.psi.*;
import org.jetbrains.yaml.psi.impl.YAMLArrayImpl;
import org.jetbrains.yaml.psi.impl.YAMLBlockMappingImpl;
import org.jetbrains.yaml.psi.impl.YAMLBlockSequenceImpl;
import org.jetbrains.yaml.psi.impl.YAMLHashImpl;

import java.util.List;

public class YAMLFoldingBuilder extends CustomFoldingBuilder {

  private static final int PLACEHOLDER_LEN = 20;

  @Override
  protected void buildLanguageFoldRegions(@NotNull List<FoldingDescriptor> descriptors,
                                          @NotNull PsiElement root,
                                          @NotNull Document document,
                                          boolean quick) {
    collectDescriptors(root, descriptors);
  }

  private static void collectDescriptors(final @NotNull PsiElement element, final @NotNull List<? super FoldingDescriptor> descriptors) {
    TextRange nodeTextRange = element.getTextRange();
    if (nodeTextRange.getLength() < 2) {
      return;
    }

    if (element instanceof YAMLDocument) {
      if (PsiTreeUtil.findChildrenOfAnyType(element.getParent(), YAMLDocument.class).size() > 1) {
        descriptors.add(new FoldingDescriptor(element, nodeTextRange));
      }
    }
    else if (element instanceof YAMLScalar && ((YAMLScalar)element).isMultiline()
             ||
             element instanceof YAMLKeyValue && ((YAMLKeyValue)element).getValue() instanceof YAMLCompoundValue
             ||
             element instanceof YAMLSequenceItem && ((YAMLSequenceItem)element).getValue() instanceof YAMLCompoundValue) {
      descriptors.add(new FoldingDescriptor(element, nodeTextRange));
    }

    PsiElement child = element.getFirstChild();
    while (child != null) {
      child = foldComments(child, descriptors);
      if (child == null) break;
      collectDescriptors(child, descriptors);
      child = child.getNextSibling();
    }
  }

  private static @Nullable PsiElement foldComments(PsiElement child, @NotNull List<? super FoldingDescriptor> descriptors) {
    PsiComment startComment = null;
    PsiComment endComment = null;
    int commentsCount = 0;
    while (child instanceof PsiComment) {
      commentsCount++;
      if (startComment == null) {
        startComment = (PsiComment)child;
      }
      endComment = (PsiComment)child;
      child = skipSpaceElementsUpToLine(child.getNextSibling());
    }
    if (commentsCount > 2) {
      descriptors.add(new FoldingDescriptor(startComment,
                                            TextRange.create(startComment.getTextRange().getStartOffset(),
                                                             endComment.getTextRange().getEndOffset()
                                            )
      ));
    }
    return child;
  }

  private static PsiElement skipSpaceElementsUpToLine(PsiElement element) {
    int eol = 0;
    while (element != null) {
      IElementType elementType = PsiUtilCore.getElementType(element);
      if (YAMLElementTypes.EOL_ELEMENTS.contains(elementType)) {
        if (eol > 0) break;
        eol++;
      }
      if (!YAMLElementTypes.SPACE_ELEMENTS.contains(elementType)) break;
      element = element.getNextSibling();
    }
    return element;
  }

  @Override
  protected @Nullable String getLanguagePlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
    return getPlaceholderText(SourceTreeToPsiMap.treeElementToPsi(node));
  }

  private static @NotNull String getPlaceholderText(@Nullable PsiElement psiElement) {

    if (psiElement instanceof YAMLDocument) {
      return "---";
    }
    else if (psiElement instanceof PsiComment) {
      return "# ...";
    }
    else if (psiElement instanceof YAMLScalar) {
      return normalizePlaceHolderText(((YAMLScalar)psiElement).getTextValue());
    }
    else if (psiElement instanceof YAMLSequence) {
      final int size = ((YAMLSequence)psiElement).getItems().size();
      final String placeholder = size + " " + StringUtil.pluralize("item", size);
      if (psiElement instanceof YAMLArrayImpl) {
        return "[" + placeholder + "]";
      }
      else if (psiElement instanceof YAMLBlockSequenceImpl) {
        return "<" + placeholder + ">";
      }
    }
    else if (psiElement instanceof YAMLMapping) {
      final int size = ((YAMLMapping)psiElement).getKeyValues().size();
      final String placeholder = size + " " + StringUtil.pluralize("key", size);
      return "{" + placeholder + "}";
    }
    else if (psiElement instanceof YAMLKeyValue) {
      return normalizePlaceHolderText(((YAMLKeyValue)psiElement).getKeyText())
             + ": "
             + getPlaceholderText(((YAMLKeyValue)psiElement).getValue());
    }
    else if (psiElement instanceof YAMLSequenceItem) {
      return "- "
             + getPlaceholderText(((YAMLSequenceItem)psiElement).getValue());
    }
    return "...";
  }

  @Override
  protected boolean isRegionCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }

  private static String normalizePlaceHolderText(@Nullable String text) {
    if (text == null) {
      return null;
    }

    if (text.length() <= PLACEHOLDER_LEN) {
      return text;
    }
    return StringUtil.trimMiddle(text, PLACEHOLDER_LEN);
  }
}
