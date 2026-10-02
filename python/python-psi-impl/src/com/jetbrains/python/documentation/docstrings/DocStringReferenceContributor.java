// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
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
