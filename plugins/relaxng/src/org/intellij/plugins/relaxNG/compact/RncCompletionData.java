/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.CompletionData;
import com.intellij.codeInsight.completion.CompletionVariant;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.PatternFilter;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.relaxNG.compact.psi.RncDecl;
import org.intellij.plugins.relaxNG.compact.psi.RncDefine;
import org.intellij.plugins.relaxNG.compact.psi.RncElement;
import org.intellij.plugins.relaxNG.compact.psi.RncGrammar;
import org.intellij.plugins.relaxNG.compact.psi.util.EscapeUtil;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.*;

public class RncCompletionData extends CompletionData {

  public RncCompletionData() {
    declareFinalScope(RncElement.class);

    final CompletionVariant variant = new CompletionVariant(new AndFilter(
            new ElementFilter() {
              public boolean isAcceptable(Object element, PsiElement context) {
                return true;
              }

              public boolean isClassAcceptable(Class hintClass) {
                return PsiElement.class.isAssignableFrom(hintClass);
              }
            },
            new PatternFilter(or(
                    psiElement().afterLeaf(psiElement(RncTokenTypes.KEYWORD_DEFAULT)),
                    not(
                            or(
                                    psiElement().inside(psiElement(RncTokenTypes.LITERAL)),
                                    psiElement().afterLeaf(psiElement().withElementType(RncTokenTypes.KEYWORDS))
                            )
                    )
            ))
    ));

    variant.includeScopeClass(LeafPsiElement.class, true);

    variant.addCompletion(new KeywordGetter());

    registerVariant(variant);
  }

  private static class KeywordGetter implements ContextGetter {
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

    public Object[] get(PsiElement context, CompletionContext completionContext) {
      return ContainerUtil.map2Array(doGetKeywords(context), LookupElement.class, new Function<String, LookupElement>() {
        public LookupElement fun(String s) {
          return LookupElementFactory.getInstance().createLookupElement(s).setTailType(TailType.SPACE).setBold();
        }
      });
    }

    private String[] doGetKeywords(PsiElement context) {
      final PsiElement next = PsiTreeUtil.skipSiblingsForward(context, PsiWhiteSpace.class);
      if (next != null && EscapeUtil.unescapeText(next).equals("=")) {
          return new String[]{ "start" };
        }

      if (DEFAULT_PATTERN.accepts(context)) {
        return new String[]{ "namespace" };
      } else if (DECL_PATTERN.accepts(context)) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      } else if (context.getParent() instanceof RncDefine && context.getParent().getFirstChild() == context) {
        if (DEFINE_PATTERN.accepts(context)) {
          return ArrayUtil.EMPTY_STRING_ARRAY;
        }
        if (TOP_LEVEL.accepts(context)) {
          if (!afterPattern(context)) {
            return ArrayUtil.mergeArrays(DECL_KEYWORDS,
                    ArrayUtil.mergeArrays(GRAMMAR_CONTENT_KEYWORDS, PATTERN_KEYWORDS, String.class), String.class);
          }
        }
        return GRAMMAR_CONTENT_KEYWORDS;
      }
      return PATTERN_KEYWORDS;
    }

    private boolean afterPattern(PsiElement context) {
      // TODO: recognize all patterns
      return PsiTreeUtil.getPrevSiblingOfType(context.getParent(), RncDefine.class) != null;
    }
  }
}