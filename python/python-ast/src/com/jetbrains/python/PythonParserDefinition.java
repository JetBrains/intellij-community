// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
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
import com.jetbrains.python.ast.impl.PyAstFileElementType;
import com.jetbrains.python.lexer.PythonIndentingLexer;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.psi.PyElementType;
import org.jetbrains.annotations.NotNull;

/**
 * The parser definition of Python in every product mode. The PSI classes come from {@link PyPsiElementFactory}.
 */
public class PythonParserDefinition implements ParserDefinition {
  /** The file node type of a {@code .py} file. */
  public static final IFileElementType PYTHON_FILE = new PyAstFileElementType(PythonLanguage.INSTANCE);

  @Override
  public @NotNull Lexer createLexer(Project project) {
    return new PythonIndentingLexer();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return PYTHON_FILE;
  }

  @Override
  public @NotNull TokenSet getWhitespaceTokens() {
    return TokenSet.create(PyTokenTypes.LINE_BREAK, PyTokenTypes.SPACE, PyTokenTypes.TAB, PyTokenTypes.FORMFEED);
  }

  @Override
  public @NotNull TokenSet getCommentTokens() {
    return TokenSet.create(PyTokenTypes.END_OF_LINE_COMMENT);
  }

  @Override
  public @NotNull TokenSet getStringLiteralElements() {
    return TokenSet.orSet(PyTokenTypes.STRING_NODES, PyTokenTypes.FSTRING_TOKENS);
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new PyParser();
  }

  @Override
  public @NotNull PsiElement createElement(@NotNull ASTNode node) {
    final IElementType type = node.getElementType();
    if (type instanceof PyElementType pyElType) {
      return pyElType.createElement(node);
    }
    return new ASTWrapperPsiElement(node);
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return PyPsiElementFactory.current().createPythonFile(viewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    // see LanguageTokenSeparatorGenerator instead
    return SpaceRequirements.MAY;
  }
}
