/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageUtil;
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
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.PyElementType;

/**
 * @author yole
 * @author Keith Lea
 */
public class PythonParserDefinition implements ParserDefinition {
  private PythonLanguage language;

  private TokenSet myWhitespaceTokens;
  private TokenSet myCommentTokens;

  public PythonParserDefinition() {
    language = (PythonLanguage)PythonFileType.INSTANCE.getLanguage();

    myWhitespaceTokens = TokenSet.create(PyTokenTypes.LINE_BREAK, PyTokenTypes.SPACE, PyTokenTypes.TAB, PyTokenTypes.FORMFEED);
    myCommentTokens = TokenSet.create(PyTokenTypes.END_OF_LINE_COMMENT);
  }

  @NotNull
  public Lexer createLexer(Project project) {
    return new PythonIndentingLexer();
  }

  public IFileElementType getFileNodeType() {
    return language.getFileElementType();
  }

  @NotNull
  public TokenSet getWhitespaceTokens() {
    return myWhitespaceTokens;
  }

  @NotNull
  public TokenSet getCommentTokens() {
    return myCommentTokens;
  }

  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @NotNull
  public PsiParser createParser(Project project) {
    return new PyParser();
  }

  @NotNull
  public PsiElement createElement(ASTNode node) {
    final IElementType type = node.getElementType();
    if (type instanceof PyElementType) {
      PyElementType pyElType = (PyElementType)type;
      return pyElType.createElement(node);
    }

    return new ASTWrapperPsiElement(node);
  }

  public PsiFile createFile(FileViewProvider viewProvider) {
    return new PyFileImpl(viewProvider);
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    final Lexer lexer = createLexer(left.getPsi().getProject());
    return LanguageUtil.canStickTokensTogetherByLexer(left, right, lexer, 0);
  }

}
