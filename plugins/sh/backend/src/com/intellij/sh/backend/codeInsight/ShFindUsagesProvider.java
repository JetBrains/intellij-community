// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.codeInsight;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.sh.ShBundle;
import com.intellij.sh.lexer.ShLexer;
import com.intellij.sh.psi.ShFunctionDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.sh.lexer.ShTokenTypes.*;

final class ShFindUsagesProvider implements FindUsagesProvider {
  @Override
  public @NotNull WordsScanner getWordsScanner() {
    return new DefaultWordsScanner(new ShLexer(), TokenSet.create(WORD), commentTokens, literals);
  }

  @Override
  public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
    return psiElement instanceof ShFunctionDefinition;
  }

  @Override
  public @Nullable String getHelpId(@NotNull PsiElement psiElement) {
    return null;
  }

  @Override
  public @NotNull String getType(@NotNull PsiElement element) {
    return ShBundle.message("find.usages.type.function");
  }

  @Override
  public @NotNull String getDescriptiveName(@NotNull PsiElement element) {
    if (element instanceof PsiNamedElement) {
      String name = ((PsiNamedElement)element).getName();
      if (name != null) return name;
    }
    return element.getText();
  }

  @Override
  public @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    return element.getText();
  }
}