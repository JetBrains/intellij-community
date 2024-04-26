// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.xml.PossiblePrefixReference;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class SchemaPrefixRenameHandler extends VariableInplaceRenameHandler {
  
  private static @Nullable PossiblePrefixReference getReference(PsiFile file, Editor editor) {
    if (file != null && editor != null) {
      int offset = editor.getCaretModel().getOffset();
      PsiReference reference = file.findReferenceAt(offset);
      if (reference instanceof PossiblePrefixReference) {
        return (PossiblePrefixReference)reference;
      }
    }
    return null;

  }

  @Override
  protected boolean isAvailable(@Nullable PsiElement element, @NotNull Editor editor, @NotNull PsiFile file) {
    PossiblePrefixReference ref = getReference(file, editor);
    return ref != null && ref.resolve() instanceof SchemaPrefix;
  }

  @Override
  protected VariableInplaceRenamer createRenamer(@NotNull PsiElement elementToRename, @NotNull Editor editor) {
    PossiblePrefixReference reference = getReference(elementToRename.getContainingFile(), editor);
    if (reference != null) {
      PsiElement prefix = reference.resolve();
      if (prefix instanceof SchemaPrefix) {
        return new VariableInplaceRenamer((PsiNamedElement)prefix, editor) {
          @Override
          protected void addReferenceAtCaret(Collection<? super PsiReference> refs) {}

          @Override
          protected boolean isReferenceAtCaret(PsiElement selectedElement, PsiReference ref) {
            return false;
          }
        };
      }
    }
    return null;
  }
}
