// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.FactoryMap;
import kotlin.text.StringsKt;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

class YAMLFormattingContext {
  private final static Indent DIRECT_NORMAL_INDENT = Indent.getNormalIndent(true);
  private final static Indent SAME_AS_PARENT_INDENT = Indent.getSpaceIndent(0, true);
  private final static Indent SAME_AS_INDENTED_ANCESTOR_INDENT = Indent.getSpaceIndent(0);

  @NotNull
  public final CodeStyleSettings mySettings;
  @NotNull
  private final PsiFile myFile;
  @NotNull
  private final SpacingBuilder mySpaceBuilder;

  /** This alignments increase partial reformatting stability in case of initially incorrect indents */
  @NotNull
  private final Map<ASTNode, Alignment> myChildIndentAlignments = FactoryMap.create(node -> Alignment.createAlignment(true));

  @NotNull
  private final Map<ASTNode, Alignment> myChildValueAlignments = FactoryMap.create(node -> Alignment.createAlignment(true));

  private final boolean shouldIndentSequenceValue;
  private final boolean shouldInlineSequenceIntoSequence;
  private final boolean shouldInlineBlockMappingIntoSequence;
  private final int getValueAlignment;

  @Nullable
  private String myFullText = null;

  YAMLFormattingContext(@NotNull CodeStyleSettings settings, @NotNull PsiFile file) {
    mySettings = settings;
    myFile = file;
    YAMLCodeStyleSettings custom = mySettings.getCustomSettings(YAMLCodeStyleSettings.class);
    CommonCodeStyleSettings common = mySettings.getCommonSettings(YAMLLanguage.INSTANCE);
    mySpaceBuilder = new SpacingBuilder(mySettings, YAMLLanguage.INSTANCE)
      .between(YAMLTokenTypes.COLON, YAMLElementTypes.KEY_VALUE_PAIR).lineBreakInCode()
      .between(YAMLTokenTypes.COLON, YAMLElementTypes.SEQUENCE_ITEM).lineBreakInCode()
      .between(YAMLElementTypes.ALIAS_NODE, YAMLTokenTypes.COLON).spaces(1)
      .before(YAMLTokenTypes.COLON).spaceIf(custom.SPACE_BEFORE_COLON)
      .after(YAMLTokenTypes.COLON).spaces(1)
      .after(YAMLTokenTypes.LBRACKET).spaceIf(common.SPACE_WITHIN_BRACKETS)
      .before(YAMLTokenTypes.RBRACKET).spaceIf(common.SPACE_WITHIN_BRACKETS)
      .after(YAMLTokenTypes.LBRACE).spaceIf(common.SPACE_WITHIN_BRACES)
      .before(YAMLTokenTypes.RBRACE).spaceIf(common.SPACE_WITHIN_BRACES)
    ;
    shouldIndentSequenceValue = custom.INDENT_SEQUENCE_VALUE;
    shouldInlineSequenceIntoSequence = !custom.SEQUENCE_ON_NEW_LINE;
    shouldInlineBlockMappingIntoSequence = !custom.BLOCK_MAPPING_ON_NEW_LINE;
    getValueAlignment = custom.ALIGN_VALUES_PROPERTIES;
  }

  private static final TokenSet NON_SIGNIFICANT_TOKENS_BEFORE_TEMPLATE =
    TokenSet.create(TokenType.WHITE_SPACE, YAMLTokenTypes.SEQUENCE_MARKER);

  private static boolean isAfterKey(ASTNode node) {
    List<ASTNode> nodes = StreamEx.iterate(node, Objects::nonNull, TreeUtil::prevLeaf).skip(1)
      .dropWhile(n -> NON_SIGNIFICANT_TOKENS_BEFORE_TEMPLATE.contains(n.getElementType())).limit(2).toList();
    if (nodes.size() != 2) return false;
    return YAMLTokenTypes.COLON.equals(nodes.get(0).getElementType()) && YAMLTokenTypes.SCALAR_KEY.equals(nodes.get(1).getElementType());
  }

  private static boolean isAfterSequenceMarker(ASTNode node) {
    List<ASTNode> nodes = StreamEx.iterate(node, Objects::nonNull, n -> n.getTreePrev()).skip(1)
      .filter(n -> !YAMLElementTypes.SPACE_ELEMENTS.contains(n.getElementType()))
      .takeWhile(n -> !YAMLTokenTypes.EOL.equals(n.getElementType())).limit(2).toList();
    if (nodes.size() != 1) return false;
    return YAMLTokenTypes.SEQUENCE_MARKER.equals(nodes.get(0).getElementType());
  }

  private static boolean isAdjectiveToMinus(ASTNode node) {
    ASTNode prevLeaf = TreeUtil.prevLeaf(node);
    // we don't consider`-` before template as a seq marker if there is no space before it, because it could be a `-1` value for instance
    return prevLeaf != null && YAMLTokenTypes.SEQUENCE_MARKER.equals(prevLeaf.getElementType());
  }

  @Nullable
  Spacing computeSpacing(@NotNull Block parent, @Nullable Block child1, @NotNull Block child2) {
    if (child1 instanceof ASTBlock && endsWithTemplate(((ASTBlock)child1).getNode())) {
      return null;
    }

    if (child2 instanceof ASTBlock && startsWithTemplate(((ASTBlock)child2).getNode())) {
      ASTNode astNode = ((ASTBlock)child2).getNode();
      if (!isAdjectiveToMinus(astNode)) {
        if (isAfterKey(astNode)) {
          return mySpaceBuilder.getSpacing(parent, getNodeElementType(parent), YAMLTokenTypes.COLON, YAMLTokenTypes.SCALAR_TEXT);
        }
        if (isAfterSequenceMarker(astNode)) {
          return getSpacingAfterSequenceMarker(child1, child2);
        }
      }
      return null;
    }

    Spacing simpleSpacing = mySpaceBuilder.getSpacing(parent, child1, child2);
    if (simpleSpacing != null) {
      return simpleSpacing;
    }

    return getSpacingAfterSequenceMarker(child1, child2);
  }

  private Spacing getSpacingAfterSequenceMarker(Block child1, Block child2) {
    if (!(child1 instanceof ASTBlock && child2 instanceof ASTBlock)) {
      return null;
    }
    ASTNode node1 = ((ASTBlock)child1).getNode();
    ASTNode node2 = ((ASTBlock)child2).getNode();
    if (PsiUtilCore.getElementType(node1) != YAMLTokenTypes.SEQUENCE_MARKER) {
      return null;
    }
    IElementType node2Type = PsiUtilCore.getElementType(node2);
    int indentSize = mySettings.getIndentSize(YAMLFileType.YML);
    if (indentSize < 2) {
      indentSize = 2;
    }

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

  private static @Nullable IElementType getNodeElementType(Block parent) {
    if (parent == null) return null;
    ASTBlock it = ObjectUtils.tryCast(parent, ASTBlock.class);
    if (it == null) return null;
    ASTNode node = it.getNode();
    if (node == null) return null;
    return node.getElementType();
  }

  private static boolean startsWithTemplate(@Nullable ASTNode astNode) {
    while (astNode != null) {
      if (astNode instanceof OuterLanguageElement) return true;
      if (NON_SIGNIFICANT_TOKENS_BEFORE_TEMPLATE.contains(astNode.getElementType())) {
        astNode = astNode.getTreeNext();
      }
      else {
        astNode = astNode.getFirstChildNode();
      }
    }
    return false;
  }
  
  private static boolean endsWithTemplate(@Nullable ASTNode astNode) {
    while (astNode != null) {
      if (astNode instanceof OuterLanguageElement) return true;
      astNode = astNode.getLastChildNode();
    }
    return false;
  }

  @Nullable
  Alignment computeAlignment(@NotNull ASTNode node) {
    IElementType type = PsiUtilCore.getElementType(node);
    if (type == YAMLElementTypes.SEQUENCE_ITEM) {
      if (node.getTreeParent().getElementType() == YAMLElementTypes.ARRAY) {
        YAMLSequence sequence = (YAMLSequence)node.getTreeParent().getPsi();
        for (YAMLSequenceItem child : sequence.getItems()) {
          // do not align multiline elements in json-style arrays
          if (child.textContains('\n')) {
            return null;
          }
        }
      }
      // Anyway we need to align `-` symbols in block-style sequences
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
    if (node instanceof OuterLanguageElement) {
      Indent templateIndent = computeTemplateIndent(((OuterLanguageElement)node).getTextRange());
      if (templateIndent != null) return templateIndent;
    }
    IElementType nodeType = PsiUtilCore.getElementType(node);
    IElementType parentType = PsiUtilCore.getElementType(node.getTreeParent());
    IElementType grandParentType = parentType == null ? null : PsiUtilCore.getElementType(node.getTreeParent().getTreeParent());
    IElementType grand2ParentType = grandParentType == null ? null :
                                    PsiUtilCore.getElementType(node.getTreeParent().getTreeParent().getTreeParent());

    assert nodeType != YAMLElementTypes.SEQUENCE : "Sequence should be inlined!";
    assert nodeType != YAMLElementTypes.MAPPING : "Mapping should be inlined!";
    assert nodeType != YAMLElementTypes.DOCUMENT : "Document should be inlined!";

    if (YAMLElementTypes.DOCUMENT_BRACKETS.contains(nodeType)) {
      return SAME_AS_PARENT_INDENT;
    }
    else if (YAMLElementTypes.BRACKETS.contains(nodeType)) {
      return SAME_AS_INDENTED_ANCESTOR_INDENT;
    }
    else if (YAMLElementTypes.TEXT_SCALAR_ITEMS.contains(nodeType)) {
      if (grandParentType == YAMLElementTypes.DOCUMENT) {
        return SAME_AS_PARENT_INDENT;
      }
      if (grand2ParentType == YAMLElementTypes.ARRAY || grand2ParentType == YAMLElementTypes.HASH) {
        return Indent.getContinuationWithoutFirstIndent();
      }
      return DIRECT_NORMAL_INDENT;
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
      if (nodeType == YAMLTokenTypes.COMMENT) {
        if (parentType == YAMLElementTypes.SEQUENCE) {
          return computeSequenceItemIndent(node);
        }
        if (parentType == YAMLElementTypes.MAPPING) {
          return computeKeyValuePairIndent(node);
        }
      }
      return YAMLElementTypes.TOP_LEVEL.contains(parentType) ? SAME_AS_PARENT_INDENT : null;
    }
  }

  @Nullable
  private Indent computeTemplateIndent(TextRange nodeTextRange) {
    Document document = PsiDocumentManager.getInstance(myFile.getProject()).getDocument(myFile);
    if (document == null) return null;
    int lineNumber = document.getLineNumber(nodeTextRange.getStartOffset());
    int lineStartOffset = document.getLineStartOffset(lineNumber);

    if (!StringsKt.isBlank(document.getCharsSequence().subSequence(lineStartOffset, nodeTextRange.getStartOffset()))) return null;

    return new IndentImpl(Indent.Type.SPACES, true, nodeTextRange.getStartOffset() - lineStartOffset, false, false);
  }

  @Nullable
  Indent computeNewChildIndent(@NotNull ASTNode node) {
    return YAMLElementTypes.TOP_LEVEL.contains(PsiUtilCore.getElementType(node))
           ? SAME_AS_PARENT_INDENT
           : DIRECT_NORMAL_INDENT;
  }


  public boolean isIncomplete(@NotNull ASTNode node) {
    Predicate<YAMLValue> possiblyIncompleteValue = value ->
      value == null || YAMLElementTypes.INCOMPLETE_BLOCKS.contains(PsiUtilCore.getElementType(value));

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

  @NotNull
  public String getFullText() {
    if (myFullText == null) {
      myFullText = myFile.getText();
    }
    return myFullText;
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
      return Indent.getNoneIndent();
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
      return Indent.getNormalIndent();
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
