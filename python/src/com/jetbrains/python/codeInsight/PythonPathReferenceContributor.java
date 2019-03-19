// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.codeInsight.completion.PyPathCompletionContributor.pyStringLiteralMatches;

public class PythonPathReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(pyStringLiteralMatches("[/\\\\]"),
                                        new PythonPathReferenceProvider());
  }
}
