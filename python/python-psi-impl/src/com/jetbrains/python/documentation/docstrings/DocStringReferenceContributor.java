/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.documentation.docstrings;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;


public final class DocStringReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(DocStringTagCompletionContributor.Helper.DOCSTRING_PATTERN,
                                        new DocStringReferenceProvider());
    // Sphinx cross-reference roles also work in line comments (e.g. `# see :py:class:`Foo``).
    registrar.registerReferenceProvider(PlatformPatterns.psiComment(), new SphinxCommentReferenceProvider());
  }

  private static final class SphinxCommentReferenceProvider extends PsiReferenceProvider {
    @Override
    public boolean acceptsTarget(@NotNull PsiElement target) {
      return target instanceof PyElement;
    }

    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
      if (!(element instanceof PsiComment comment)) {
        return PsiReference.EMPTY_ARRAY;
      }
      return SphinxReferences.INSTANCE.findReferences(comment).toArray(PsiReference.EMPTY_ARRAY);
    }
  }
}
