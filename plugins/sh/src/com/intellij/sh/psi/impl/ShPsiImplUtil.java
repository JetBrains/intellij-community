// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.sh.ShSupport;
import com.intellij.sh.psi.ShFunctionDefinition;
import com.intellij.sh.psi.ShLiteral;
import com.intellij.sh.psi.ShString;
import com.intellij.sh.psi.ShVariable;
import org.jetbrains.annotations.NotNull;

public class ShPsiImplUtil {
  static PsiReference @NotNull [] getReferences(@NotNull ShLiteral o) {
    return o instanceof ShString || o.getWord() != null
           ? ReferenceProvidersRegistry.getReferencesFromProviders(o)
           : PsiReference.EMPTY_ARRAY;
  }

  static PsiReference @NotNull [] getReferences(@NotNull ShVariable o) {
    return ShSupport.getInstance().getVariableReferences(o);
  }

  static int getTextOffset(@NotNull ShFunctionDefinition f) {
    PsiElement word = f.getWord();
    return word != null ? word.getTextOffset() : f.getNode().getStartOffset();
  }
}
