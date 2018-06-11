// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.formatter;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.*;

import java.util.Map;
import java.util.function.Predicate;

class YAMLFormattingContext {
  private final static Indent DIRECT_NORMAL_INDENT = Indent.getNormalIndent(true);
  private final static Indent SAME_AS_PARENT_INDENT = Indent.getSpaceIndent(0, true);
  private final static Indent SAME_AS_INDENTED_ANCESTOR_INDENT = Indent.getSpaceIndent(0);

  @NotNull
  public final CodeStyleSettings mySettings;

  /** This alignments increase partial reformatting stability in case of initially incorrect indents */
  @NotNull
  private final Map<ASTNode, Alignment> myChildIndentAlignments = FactoryMap.create(node -> Alignment.createAlignment(true));

  @NotNull
  private final Map<ASTNode, Alignment> myChildValueAlignments = FactoryMap.create(node -> Alignment.createAlignment(true));

  private final boolean shouldIndentSequenceValue;
  private final boolean shouldInlineSequenceIntoSequence;
  private final boolean shouldInlineBlockMappingIntoSequence;
  private final int getValueAlignment;

  YAMLFormattingContext(@NotNull CodeStyleSettings settings) {
    mySettings = settings;
    shouldIndentSequenceValue = mySettings.getCustomSettings(YAMLCodeStyleSettings.class).INDENT_SEQUENCE_VALUE;
    shouldInlineSequenceIntoSequence = !mySettings.getCustomSettings(YAMLCodeStyleSettings.class).SEQUENCE_ON_NEW_LINE;
    shouldInlineBlockMappingIntoSequence = !mySettings.getCustomSettings(YAMLCodeStyleSettings.class).BLOCK_MAPPING_ON_NEW_LINE;
    getValueAlignment = mySettings.getCustomSettings(YAMLCodeStyleSettings.class).ALIGN_VALUES_PROPERTIES;
  }

  @Nullable
  Spacing computeSpacing(@Nullable Block child1, @NotNull Block child2) {
    if (!(child1 instanceof AbstractBlock && child2 instanceof AbstractBlock)) {
      return null;
    }
    ASTNode node1 = ((AbstractBlock)child1).getNode();
    ASTNode node2 = ((AbstractBlock)child2).getNode();
    if (PsiUtilCore.getElementType(node1) != YAMLTokenTypes.SEQUENCE_MARKER) {
      return null;
    }
    IElementType node2Type = PsiUtilCore.getElementType(node2);
    int indentSize = mySettings.getIndentSize(YAMLFileType.YML);
    if (indentSize < 2) indentSize = 2;

    int spaces = 1;
    int minLineFeeds = 0;
    if (node2Type == YAMLElementTypes.SEQUENCE_ITEM) {
      if (shouldInlineSequenceIntoSequence) {
        // Set spaces to fit other items indent:
        // -   - a # 3 spaces here if indent size is 4
        //     - b
        spaces = indentSize - 1;
      }
      else {
        minLineFeeds = 1;
      }
    }
    else if (node2Type == YAMLElementTypes.KEY_VALUE_PAIR) {
      if (shouldInlineBlockMappingIntoSequence) {
        // Set spaces to fit other items indent:
        // -   a: x # 3 spaces here if indent size is 4
        //     b: y
        spaces = indentSize - 1;
      }
      else {
        minLineFeeds = 1;
      }
    }
    return Spacing.createSpacing(spaces, spaces, minLineFeeds, false, 0);
  }

  @Nullable
  Alignment computeAlignment(@NotNull ASTNode node) {
    IElementType type = PsiUtilCore.getElementType(node);
    if (type == YAMLElementTypes.SEQUENCE_ITEM) {
      return myChildIndentAlignments.get(node.getTreeParent());
    }
    if (type == YAMLElementTypes.KEY_VALUE_PAIR) {
      return myChildIndentAlignments.get(node.getTreeParent());
    }
    if (getValueAlignment == YAMLCodeStyleSettings.ALIGN_ON_COLON) {
      if (type == YAMLTokenTypes.COLON) {
        return myChildValueAlignments.get(node.getTreeParent().getTreeParent());
      }
    }
    else if (getValueAlignment == YAMLCodeStyleSettings.ALIGN_ON_VALUE) {
      if (YAMLElementTypes.SCALAR_ITEMS.contains(type)) {
        // for block scalar here we consider only headers
        ASTNode prev = getPreviousNonBlankNode(node.getTreeParent());
        if (PsiUtilCore.getElementType(prev) == YAMLTokenTypes.COLON) {
          return myChildValueAlignments.get(prev.getTreeParent().getTreeParent());
        }
      }
    }
    return null;
  }

  @Nullable
  Indent computeBlockIndent(@NotNull ASTNode node) {
    IElementType nodeType = PsiUtilCore.getElementType(node);
    IElementType parentType = PsiUtilCore.getElementType(node.getTreeParent());
    IElementType grandParentType = parentType == null ? null : PsiUtilCore.getElementType(node.getTreeParent().getTreeParent());
    boolean grandParentIsDocument = grandParentType == YAMLElementTypes.DOCUMENT;

    assert nodeType != YAMLElementTypes.SEQUENCE : "Sequence should be inlined!";
    assert nodeType != YAMLElementTypes.MAPPING  : "Mapping should be inlined!";
    assert nodeType != YAMLElementTypes.DOCUMENT : "Document should be inlined!";

    if (YAMLElementTypes.DOCUMENT_BRACKETS.contains(nodeType)) {
      return SAME_AS_PARENT_INDENT;
    }
    else if (YAMLElementTypes.BRACKETS.contains(nodeType)) {
      return SAME_AS_INDENTED_ANCESTOR_INDENT;
    }
    else if (nodeType == YAMLTokenTypes.TEXT) {
      return grandParentIsDocument ? SAME_AS_PARENT_INDENT : DIRECT_NORMAL_INDENT;
    }
    else if (nodeType == YAMLElementTypes.FILE) {
      return SAME_AS_PARENT_INDENT;
    }
    else if (YAMLElementTypes.SCALAR_VALUES.contains(nodeType)) {
      return DIRECT_NORMAL_INDENT;
    }
    else if (nodeType == YAMLElementTypes.SEQUENCE_ITEM) {
      return computeSequenceItemIndent(node);
    }
    else if (nodeType == YAMLElementTypes.KEY_VALUE_PAIR) {
      return computeKeyValuePairIndent(node);
    }
    else {
      return YAMLElementTypes.TOP_LEVEL.contains(parentType) ? SAME_AS_PARENT_INDENT : null;
    }
  }

  @Nullable
  Indent computeNewChildIndent(@NotNull ASTNode node) {
    return YAMLElementTypes.TOP_LEVEL.contains(PsiUtilCore.getElementType(node))
           ? SAME_AS_PARENT_INDENT
           : DIRECT_NORMAL_INDENT;
  }


  public boolean isIncomplete(@NotNull ASTNode node) {
    Predicate<YAMLValue> possiblyIncompleteValue = value ->
      value == null ||
      value instanceof YAMLCompoundValue ||
      value instanceof YAMLBlockScalar;
    if (PsiUtilCore.getElementType(node) == YAMLElementTypes.KEY_VALUE_PAIR) {
      YAMLValue value = ((YAMLKeyValue)node.getPsi()).getValue();
      if (possiblyIncompleteValue.test(value)) {
        return true;
      }
    }
    else if (PsiUtilCore.getElementType(node) == YAMLElementTypes.SEQUENCE_ITEM) {
      YAMLValue value = ((YAMLSequenceItem)node.getPsi()).getValue();
      if (possiblyIncompleteValue.test(value)) {
        return true;
      }
    }
    return FormatterUtil.isIncomplete(node);
  }

  @Nullable
  private static Indent computeKeyValuePairIndent(@NotNull ASTNode node) {
    IElementType parentType = PsiUtilCore.getElementType(node.getTreeParent());
    IElementType grandParentType = parentType == null ? null : PsiUtilCore.getElementType(node.getTreeParent().getTreeParent());
    boolean grandParentIsDocument = grandParentType == YAMLElementTypes.DOCUMENT;

    if (parentType == YAMLElementTypes.HASH) {
      // {
      //   key: value
      // }
      return Indent.getNormalIndent();
    } else if (grandParentIsDocument) {
      // ---
      // key: value
      return SAME_AS_PARENT_INDENT;
    } else if (parentType == YAMLElementTypes.SEQUENCE_ITEM) {
      // [
      //   a: x,
      //   b: y
      // ]
      return Indent.getNormalIndent();
    } else {
      // - - a: x
      //     b: y
      return DIRECT_NORMAL_INDENT;
    }
  }

  @NotNull
  private Indent computeSequenceItemIndent(@NotNull ASTNode node) {
    IElementType parentType = PsiUtilCore.getElementType(node.getTreeParent());
    IElementType grandParentType = parentType == null ? null : PsiUtilCore.getElementType(node.getTreeParent().getTreeParent());
    boolean grandParentIsDocument = grandParentType == YAMLElementTypes.DOCUMENT;

    if (parentType == YAMLElementTypes.ARRAY) {
      // such item should contain only one child and this child will decide indent
      // here could be just none
      return Indent.getNoneIndent();
    }
    else if (grandParentType == YAMLElementTypes.KEY_VALUE_PAIR) {
      if (shouldIndentSequenceValue) {
        // key:
        //   - x
        //   - y
        return DIRECT_NORMAL_INDENT;
      }
      else {
        // key:
        // - x
        // - y
        return SAME_AS_PARENT_INDENT;
      }
    }
    else if (grandParentIsDocument) {
      return SAME_AS_PARENT_INDENT;
    }
    else {
      // - - x
      //   - y
      // or
      // -
      //   - x
      //   - y
      return DIRECT_NORMAL_INDENT;
    }
  }

  @Nullable
  private static ASTNode getPreviousNonBlankNode(ASTNode node) {
    do {
      node = TreeUtil.prevLeaf(node);
      if (!YAMLElementTypes.BLANK_ELEMENTS.contains(PsiUtilCore.getElementType(node))) {
        return node;
      }
    } while (node != null);
    return null;
  }
}
