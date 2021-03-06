// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.relaxNG.compact;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import org.intellij.plugins.relaxNG.compact.psi.RncDecl;
import org.intellij.plugins.relaxNG.compact.psi.RncDefine;
import org.intellij.plugins.relaxNG.compact.psi.RncGrammar;
import org.intellij.plugins.relaxNG.compact.psi.util.EscapeUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.and;
import static com.intellij.patterns.StandardPatterns.not;

/**
 * @author Dennis.Ushakov
 */
public class RncCompletionContributor extends CompletionContributor {
  private static final ElementPattern TOP_LEVEL =
    not(psiElement().inside(psiElement(RncGrammar.class)
                              .inside(true, psiElement(RncGrammar.class))));

  private static final PsiElementPattern DECL_PATTERN =
    psiElement().inside(psiElement(RncDecl.class));

  private static final PsiElementPattern DEFAULT_PATTERN =
    DECL_PATTERN.afterLeaf(psiElement().withText("default"));

  private static final ElementPattern DEFINE_PATTERN =
    and(psiElement().withParent(RncDefine.class), psiElement().afterLeafSkipping(psiElement(PsiWhiteSpace.class), psiElement().withText("=")));

  private static final String[] DECL_KEYWORDS = new String[]{ "default", "namespace", "datatypes" };
  private static final String[] GRAMMAR_CONTENT_KEYWORDS = new String[]{ "include", "div", "start" };
  private static final String[] PATTERN_KEYWORDS = new String[]{ "attribute", "element", "grammar",
    "notAllowed", "text", "empty", "external", "parent", "list", "mixed" };


  public RncCompletionContributor() {
    CompletionProvider<CompletionParameters> provider = new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        String[] keywords = getKeywords(parameters.getPosition());
        for (String keyword : keywords) {
          result.addElement(TailTypeDecorator.withTail(LookupElementBuilder.create(keyword).bold(), TailType.SPACE));
        }
      }
    };
    extend(null, psiElement().afterLeaf(psiElement(RncTokenTypes.KEYWORD_DEFAULT)), provider);
    extend(null, psiElement().andNot(psiElement().inside(psiElement(RncTokenTypes.LITERAL))).
                              andNot(psiElement().afterLeaf(psiElement().withElementType(RncTokenTypes.KEYWORDS))), provider);
  }


  private static String[] getKeywords(PsiElement context) {
    final PsiElement next = PsiTreeUtil.skipWhitespacesForward(context);
    if (next != null && EscapeUtil.unescapeText(next).equals("=")) {
      return new String[]{ "start" };
    }

    if (DEFAULT_PATTERN.accepts(context)) {
      return new String[]{ "namespace" };
    } else if (DECL_PATTERN.accepts(context)) {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    } else if (context.getParent() instanceof RncDefine && context.getParent().getFirstChild() == context) {
      if (DEFINE_PATTERN.accepts(context)) {
        return ArrayUtilRt.EMPTY_STRING_ARRAY;
      }
      if (TOP_LEVEL.accepts(context)) {
        if (!afterPattern(context)) {
          return ArrayUtil.mergeArrays(DECL_KEYWORDS, ArrayUtil.mergeArrays(GRAMMAR_CONTENT_KEYWORDS, PATTERN_KEYWORDS));
        }
      }
      return GRAMMAR_CONTENT_KEYWORDS;
    }
    return PATTERN_KEYWORDS;
  }

  private static boolean afterPattern(PsiElement context) {
    // TODO: recognize all patterns
    return PsiTreeUtil.getPrevSiblingOfType(context.getParent(), RncDefine.class) != null;
  }
}
