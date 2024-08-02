// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.spellchecker;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.tree.TokenSet;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;

import static com.intellij.sh.ShTypes.*;
import static com.intellij.sh.lexer.ShTokenTypes.COMMENT;

public class ShSpellcheckingStrategy extends SpellcheckingStrategy implements DumbAware {
  private static final TokenSet TOKENS_WITH_TEXT = TokenSet.create(STRING_CONTENT, RAW_STRING, HEREDOC_CONTENT, COMMENT);

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    final ASTNode node = element.getNode();
    if (node != null && TOKENS_WITH_TEXT.contains(node.getElementType())) return TEXT_TOKENIZER;
    if (element instanceof PsiNameIdentifierOwner) return ShIdentifierOwnerTokenizer.INSTANCE;
    return EMPTY_TOKENIZER;
  }
}