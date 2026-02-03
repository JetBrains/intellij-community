// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.TokenSeparatorGenerator;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.lexer.PythonIndentingLexer;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class PyTokenSeparatorGenerator implements TokenSeparatorGenerator {
  @Override
  public ASTNode generateWhitespaceBetweenTokens(@Nullable ASTNode left, @NotNull ASTNode right) {
    if (left == null) {
      return null;
    }
    PsiManager manager = right.getTreeParent().getPsi().getManager();
    if (left.getElementType() == PyTokenTypes.END_OF_LINE_COMMENT) {
      return createLineBreak(manager);
    }

    if (left.getPsi().isValid() && right.getPsi().isValid()) {
      final PsiElement commonParent = PsiTreeUtil.findCommonParent(left.getPsi(), right.getPsi());
      if (commonParent == null) return null;
      final PsiElement leftPrevAncestor = PsiTreeUtil.findPrevParent(commonParent, left.getPsi());
      final PsiElement rightPrevAncestor = PsiTreeUtil.findPrevParent(commonParent, right.getPsi());

      if (isStatementOrFunction(leftPrevAncestor) && isStatementOrFunction(rightPrevAncestor)) {
        int leftIndent = PyPsiUtils.getElementIndentation(leftPrevAncestor);
        int rightIndent = PyPsiUtils.getElementIndentation(rightPrevAncestor);
        int maxIndent = Math.max(leftIndent, rightIndent);
        return createWhitespace(manager, "\n" + StringUtil.repeatSymbol(' ', maxIndent));
      }
    }

    if (right.getElementType() == PyTokenTypes.DEF_KEYWORD || right.getElementType() == PyTokenTypes.CLASS_KEYWORD) {
      return createLineBreak(manager);
    }
    if (left.getElementType() == TokenType.WHITE_SPACE || right.getElementType() == TokenType.WHITE_SPACE) {
      return null;
    }
    final PyStatement leftStatement = PsiTreeUtil.getParentOfType(left.getPsi(), PyStatement.class);
    if (leftStatement != null && !PsiTreeUtil.isAncestor(leftStatement, right.getPsi(), false)) {
      return createLineBreak(manager);
    }
    final Lexer lexer = new PythonIndentingLexer();
    if (LanguageUtil.canStickTokensTogetherByLexer(left, right, lexer) == ParserDefinition.SpaceRequirements.MUST) {
      return createSpace(manager);
    }
    return null;
  }

  private static boolean isStatementOrFunction(PsiElement element) {
    return element instanceof PyStatement;
  }

  private static ASTNode createSpace(PsiManager manager) {
    return createWhitespace(manager, " ");
  }

  private static ASTNode createLineBreak(PsiManager manager) {
    return createWhitespace(manager, "\n");
  }

  private static ASTNode createWhitespace(PsiManager manager, final String text) {
    return Factory.createSingleLeafElement(TokenType.WHITE_SPACE, text, 0, text.length(), null, manager);
  }
}
