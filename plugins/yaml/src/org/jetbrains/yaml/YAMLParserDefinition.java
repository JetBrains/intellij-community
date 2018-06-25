package org.jetbrains.yaml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.lexer.YAMLFlexLexer;
import org.jetbrains.yaml.parser.YAMLParser;
import org.jetbrains.yaml.psi.impl.*;

/**
 * @author oleg
 */
public class YAMLParserDefinition implements ParserDefinition, YAMLElementTypes {
  private static final TokenSet myCommentTokens = TokenSet.create(YAMLTokenTypes.COMMENT);

  @NotNull
  public Lexer createLexer(final Project project) {
    return new YAMLFlexLexer();
  }

  @Nullable
  public PsiParser createParser(final Project project) {
    return new YAMLParser();
  }

  public IFileElementType getFileNodeType() {
    return FILE;
  }

  @NotNull
  public TokenSet getWhitespaceTokens() {
    return TokenSet.create(YAMLTokenTypes.WHITESPACE);
  }

  @NotNull
  public TokenSet getCommentTokens() {
    return myCommentTokens;
  }

  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSet.create(YAMLTokenTypes.SCALAR_STRING, YAMLTokenTypes.SCALAR_DSTRING, YAMLTokenTypes.TEXT);
  }

  @NotNull
  public PsiElement createElement(final ASTNode node) {
    final IElementType type = node.getElementType();
    if (type == DOCUMENT){
      return new YAMLDocumentImpl(node);
    }
    if (type == KEY_VALUE_PAIR) {
      return new YAMLKeyValueImpl(node);
    }
    if (type == COMPOUND_VALUE) {
      return new YAMLCompoundValueImpl(node);
    }
    if (type == SEQUENCE) {
      return new YAMLBlockSequenceImpl(node);
    }
    if (type == MAPPING) {
      return new YAMLBlockMappingImpl(node);
    }
    if (type == SEQUENCE_ITEM) {
      return new YAMLSequenceItemImpl(node);
    }
    if (type == HASH) {
      return new YAMLHashImpl(node);
    }
    if (type == ARRAY) {
      return new YAMLArrayImpl(node);
    }
    if (type == SCALAR_LIST_VALUE) {
      return new YAMLScalarListImpl(node);
    }
    if (type == SCALAR_TEXT_VALUE) {
      return new YAMLScalarTextImpl(node);
    }
    if (type == SCALAR_PLAIN_VALUE) {
      return new YAMLPlainTextImpl(node);
    }
    if (type == SCALAR_QUOTED_STRING) {
      return new YAMLQuotedTextImpl(node);
    }
    if (type == ANCHOR_NODE) {
      return new YAMLAnchorImpl(node);
    }
    if (type == ALIAS_NODE) {
      return new YAMLAliasImpl(node);
    }
    return new YAMLPsiElementImpl(node);
  }

  public PsiFile createFile(final FileViewProvider viewProvider) {
    return new YAMLFileImpl(viewProvider);
  }

  public SpaceRequirements spaceExistenceTypeBetweenTokens(final ASTNode left, final ASTNode right) {
    return SpaceRequirements.MAY;
  }
}
