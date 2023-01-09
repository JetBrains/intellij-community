// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyQuickFixUtil {
  public static @Nullable Editor getEditor(@NotNull  PsiElement element) {
    Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());
    if (document == null) {
      return null;
    }

    EditorFactory instance = EditorFactory.getInstance();
    return instance == null ? null : instance.editors(document).findFirst().orElse(null);
  }

  public static @Nullable PsiElement dereference(PsiElement element) {
    if (element instanceof PyReferenceExpression) {
      return element.getReference().resolve();
    }
    return element;
  }
}
