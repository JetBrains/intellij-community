package org.jetbrains.yaml.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.*;
import org.jetbrains.yaml.psi.impl.YAMLArrayImpl;
import org.jetbrains.yaml.psi.impl.YAMLBlockMappingImpl;
import org.jetbrains.yaml.psi.impl.YAMLBlockSequenceImpl;
import org.jetbrains.yaml.psi.impl.YAMLHashImpl;

import java.util.LinkedList;
import java.util.List;

/**
 * @author oleg
 */
public class YAMLFoldingBuilder extends FoldingBuilderEx implements DumbAware {

  private static final int PLACEHOLDER_LEN = 10;

  @NotNull
  @Override
  public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
    List<FoldingDescriptor> descriptors = new LinkedList<>();
    collectDescriptors(root, descriptors);
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  private static void collectDescriptors(@NotNull final PsiElement element, @NotNull final List<FoldingDescriptor> descriptors) {
    final TextRange nodeTextRange = element.getTextRange();
    if (nodeTextRange.getLength() < 2) {
      return;
    }

    if (element instanceof YAMLDocument) {
      if (PsiTreeUtil.findChildrenOfAnyType(element.getParent(), YAMLDocument.class).size() > 1) {
        descriptors.add(new FoldingDescriptor(element, nodeTextRange));
      }
    }
    else if (element instanceof YAMLScalar
             || element instanceof YAMLKeyValue && ((YAMLKeyValue)element).getValue() instanceof YAMLCompoundValue) {
      descriptors.add(new FoldingDescriptor(element, nodeTextRange));
    }

    for (PsiElement child : element.getChildren()) {
      collectDescriptors(child, descriptors);
    }
  }

  @Nullable
  public String getPlaceholderText(@NotNull ASTNode node) {
    return getPlaceholderText(SourceTreeToPsiMap.treeElementToPsi(node));
  }

  @NotNull
  private static String getPlaceholderText(@Nullable PsiElement psiElement) {
    if (psiElement instanceof YAMLDocument) {
      return "---";
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
      if (psiElement instanceof YAMLHashImpl) {
        return "{" + placeholder + "}";
      }
      else if (psiElement instanceof YAMLBlockMappingImpl) {
        return "<" + placeholder + ">";
      }
    }
    else if (psiElement instanceof YAMLKeyValue) {
      return normalizePlaceHolderText(((YAMLKeyValue)psiElement).getKeyText())
             + ": "
             + getPlaceholderText(((YAMLKeyValue)psiElement).getValue());
    }
    return "...";
  }

  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }

  private static String normalizePlaceHolderText(@Nullable String text) {
    if (text == null) {
      return null;
    }

    if (text.length() <= PLACEHOLDER_LEN) {
      return text;
    }
    return text.substring(0, Math.min(text.length(), PLACEHOLDER_LEN)) + "...";
  }
}
