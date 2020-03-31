// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.jetbrains.python.lexer.PythonIndentingLexer;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyFileElementType;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 * @author Keith Lea
 */
public class PythonParserDefinition implements ParserDefinition {

  @Override
  @NotNull
  public Lexer createLexer(Project project) {
    return new PythonIndentingLexer();
  }

  @Override
  public IFileElementType getFileNodeType() {
    return PyFileElementType.INSTANCE;
  }

  @Override
  @NotNull
  public TokenSet getWhitespaceTokens() {
    return TokenSet.create(PyTokenTypes.LINE_BREAK, PyTokenTypes.SPACE, PyTokenTypes.TAB, PyTokenTypes.FORMFEED);
  }

  @Override
  @NotNull
  public TokenSet getCommentTokens() {
    return TokenSet.create(PyTokenTypes.END_OF_LINE_COMMENT);
  }

  @Override
  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSet.orSet(PyTokenTypes.STRING_NODES, PyTokenTypes.FSTRING_TOKENS);
  }

  @Override
  @NotNull
  public PsiParser createParser(Project project) {
    return new PyParser();
  }

  @Override
  @NotNull
  public PsiElement createElement(@NotNull ASTNode node) {
    final IElementType type = node.getElementType();
    if (type instanceof PyElementType) {
      PyElementType pyElType = (PyElementType)type;
      return pyElType.createElement(node);
    }
    else if (type instanceof PyStubElementType) {
      return ((PyStubElementType)type).createElement(node);
    }
    return new ASTWrapperPsiElement(node);
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new PyFileImpl(viewProvider);
  }

  @Override
  public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    // see LanguageTokenSeparatorGenerator instead
    return SpaceRequirements.MAY;
  }

}
