package com.intellij.psi.formatter.newXmlFormatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.newXmlFormatter.xml.AbstractBlock;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;

import java.util.List;

public abstract class AbstractJavaBlock extends AbstractBlock implements JavaBlock{
  protected final CodeStyleSettings mySettings;
  private final Indent myIndent;

  public AbstractJavaBlock(final ASTNode node,
                           final Wrap wrap,
                           final Alignment alignment,
                           final Indent indent,
                           final CodeStyleSettings settings) {
    super(node, wrap, alignment);
    mySettings = settings;
    myIndent = indent;
  }

  protected abstract List<Block> buildChildren();

  public static AbstractJavaBlock createJavaBlock(final ASTNode child, final CodeStyleSettings settings, final Indent indent, Wrap wrap, Alignment alignment) {
    Indent actualIndent = indent == null ? getDefaultIndent(child) : indent;
    final IElementType elementType = child.getElementType();
    if (isLikeExtendsList(elementType)) {
      return new ExtendsListBlock(child, wrap, alignment, actualIndent, settings);
    }
    else if (isBlockType(elementType)) {
      return new BlockContainingJavaBlock(child, wrap,  alignment, actualIndent, settings);
    }
    else if (elementType == ElementType.LABELED_STATEMENT) {
      return new LabeledJavaBlock(child, wrap, alignment, actualIndent,settings);
    }
    else if (elementType == JavaDocElementType.DOC_COMMENT) {
      return new DocCommentBlock(child, wrap, alignment, actualIndent, settings);
    }
    else {
      return new SimpleJavaBlock(child, wrap, alignment, actualIndent,settings);
    }
  }

  private static boolean isLikeExtendsList(final IElementType elementType) {
    return elementType == ElementType.EXTENDS_LIST
    || elementType == ElementType.IMPLEMENTS_LIST
    || elementType == ElementType.THROWS_LIST;
  }

  private static boolean isBlockType(final IElementType elementType) {
    return elementType == ElementType.SWITCH_STATEMENT
    || elementType == ElementType.FOR_STATEMENT
    || elementType == ElementType.WHILE_STATEMENT
    || elementType == ElementType.DO_WHILE_STATEMENT
    || elementType == ElementType.IF_STATEMENT
    || elementType == ElementType.METHOD
    || elementType == ElementType.FOREACH_STATEMENT;
  }

  public static AbstractJavaBlock createJavaBlock(final ASTNode child, final CodeStyleSettings settings) {
    return createJavaBlock(child, settings, getDefaultIndent(child), null, null);
  }

  private static Indent getDefaultIndent(final ASTNode child) {
    if (child.getElementType() == ElementType.CLASS) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.IF_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.TRY_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.CATCH_SECTION) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.FOR_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.FOREACH_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.BLOCK_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.DO_WHILE_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.WHILE_STATEMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.METHOD) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == JavaDocElementType.DOC_COMMENT) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == JavaDocElementType.DOC_TAG) return Formatter.getInstance().getNoneIndent();
    if (child.getElementType() == ElementType.IMPORT_LIST) return Formatter.getInstance().getNoneIndent();
    return null;
  }

  public SpaceProperty getSpaceProperty(Block child1, Block child2) {
    return new JavaSpacePropertyProcessor(((JavaBlock)child2).getFirstTreeNode(), mySettings).getResult();
  }

  public ASTNode getFirstTreeNode() {
    return myNode;
  }

  public Indent getIndent() {
    return myIndent;
  }
}
