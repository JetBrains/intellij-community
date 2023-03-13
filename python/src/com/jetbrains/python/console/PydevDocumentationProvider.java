// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.console.completion.PydevConsoleElement;
import com.jetbrains.python.console.completion.PydevConsoleReference;
import com.jetbrains.python.documentation.PyDocumentationBuilder;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PydevDocumentationProvider extends AbstractDocumentationProvider {

  @Override
  public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, final Object object, final PsiElement element) {
    if (object instanceof PydevConsoleElement) {
      return (PydevConsoleElement)object;
    }
    return super.getDocumentationElementForLookupItem(psiManager, object, element);
  }

  @Override
  public @Nls String generateDoc(final PsiElement element, @Nullable final PsiElement originalElement) {
    if (element instanceof PydevConsoleElement) {
      return PydevConsoleElement.generateDoc((PydevConsoleElement)element);
    }
    return createDoc(element, originalElement);
  }

  @Nullable
  public static @Nls String createDoc(final PsiElement element, final PsiElement originalElement) {
    if (!PythonRuntimeService.getInstance().isInPydevConsole(element)) {
      return null;
    }

    PydevConsoleReference consoleRef = PyUtil.as(element.getReference(), PydevConsoleReference.class);
    if (consoleRef == null) {
      return null;
    }
    PyElement documentationElement = consoleRef.getDocumentationElement();
    if (documentationElement == null) {
      return null;
    }

    return new PyDocumentationBuilder(documentationElement, null).build();
  }

  @Nullable
  @Override
  public PsiElement getCustomDocumentationElement(@NotNull Editor editor,
                                                  @NotNull PsiFile file,
                                                  @Nullable PsiElement contextElement,
                                                  int targetOffset) {
    if (contextElement != null && PythonRuntimeService.getInstance().isInPydevConsole(contextElement)) {
      final IElementType elementType = contextElement.getNode().getElementType();
      if (PythonDialectsTokenSetProvider.getInstance().getKeywordTokens().contains(elementType)) {
        return contextElement;
      }
      final PsiElement parent = contextElement.getParent();
      if (parent instanceof PyArgumentList && (PyTokenTypes.LPAR == elementType || PyTokenTypes.RPAR == elementType)) {
        final PyCallExpression expression = PsiTreeUtil.getParentOfType(contextElement, PyCallExpression.class);
        if (expression != null) {
          return expression.getCallee();
        }
      }
    }
      return null;
  }
}
