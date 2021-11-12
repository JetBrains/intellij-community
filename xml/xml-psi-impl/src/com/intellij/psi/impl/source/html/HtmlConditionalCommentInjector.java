// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.html;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author spleaner
 */
public class HtmlConditionalCommentInjector implements MultiHostInjector {

  /**
   * Allows to check if given element is a
   * <a href="http://msdn.microsoft.com/en-us/library/ms537512(v=vs.85).aspx">conditional comment</a>.
   *
   * @param host  target element to check
   * @return      {@code true} if given element is conditional comment; {@code false} otherwise
   */
  public static boolean isConditionalComment(@NotNull PsiElement host) {
    return parseConditionalCommentBoundaries(host) != null;
  }

  /**
   * Tries to parse given element as <a href="http://msdn.microsoft.com/en-us/library/ms537512(v=vs.85).aspx">conditional comment</a>.
   *
   * @param host  target element to parse
   * @return      {@code null} if given element is not a conditional comment;
   *              pair like {@code (conditional comment start element; conditional comment end element)} otherwise
   */
  @Nullable
  private static Pair<ASTNode, ASTNode> parseConditionalCommentBoundaries(@NotNull PsiElement host) {
    if (!(host instanceof XmlComment)) {
      return null;
    }
    final ASTNode comment = host.getNode();
    if (comment == null) {
      return null;
    }
    final ASTNode conditionalStart = comment.findChildByType(TokenSet.create(XmlTokenType.XML_CONDITIONAL_COMMENT_START_END));
    if (conditionalStart == null) {
      return null;
    }
    final ASTNode conditionalEnd = comment.findChildByType(TokenSet.create(XmlTokenType.XML_CONDITIONAL_COMMENT_END_START));
    if (conditionalEnd == null) {
      return null;
    }
    final ASTNode endOfEnd = comment.findChildByType(TokenSet.create(XmlTokenType.XML_CONDITIONAL_COMMENT_END));
    return endOfEnd == null ? null : Pair.create(conditionalStart, conditionalEnd);
  }

  @Override
  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull final PsiElement host) {
    Pair<ASTNode, ASTNode> pair = parseConditionalCommentBoundaries(host);
    if (pair == null) {
      return;
    }
    final TextRange textRange = host.getTextRange();
    final int startOffset = textRange.getStartOffset();
    Language language = host.getParent().getLanguage();
    ASTNode conditionalStart = pair.first;
    ASTNode conditionalEnd = pair.second;
    TextRange range = new UnfairTextRange(conditionalStart.getTextRange().getEndOffset() - startOffset, conditionalEnd.getStartOffset() - startOffset);
    if (range.getStartOffset() < range.getEndOffset()) {
      ASTNode current = conditionalStart.getTreeNext();
      List<TextRange> injectionsRanges = new ArrayList<>();
      while (current != conditionalEnd) {
        if (!(current.getPsi() instanceof OuterLanguageElement)) {
          injectionsRanges.add(current.getTextRange().shiftLeft(startOffset));
        }
        current = current.getTreeNext();
      }
      if (!injectionsRanges.isEmpty()) {
        registrar.startInjecting(language);
        for (TextRange injectionsRange : injectionsRanges) {
          registrar.addPlace(null, null, (PsiLanguageInjectionHost)host, injectionsRange);
        }
        registrar.doneInjecting();
      }
    }
  }

  @Override
  @NotNull
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(PsiComment.class);
  }
}
