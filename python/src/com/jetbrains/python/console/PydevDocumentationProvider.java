// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PydevDocumentationProvider extends AbstractDocumentationProvider {

  @Override
  public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, final Object object, final PsiElement element) {
    if (object instanceof PydevConsoleElement) {
      return (PydevConsoleElement) object;
    }
    return super.getDocumentationElementForLookupItem(psiManager, object, element);
  }

  @Override
  public @Nls String generateDoc(final PsiElement element, final @Nullable PsiElement originalElement) {
    // Process PydevConsoleElement case
    if (element instanceof PydevConsoleElement pydevElement) {
      return PydevConsoleElement.generateDoc(pydevElement).toString();
    }
    return null;
  }

  public static @Nullable @Nls String createDoc(final PsiElement element, final PsiElement originalElement) {
    PyReferenceExpression expression = PsiTreeUtil.getNonStrictParentOfType(originalElement, PyReferenceExpression.class);
    if (expression == null){
      expression = PsiTreeUtil.getNonStrictParentOfType(element, PyReferenceExpression.class);
      if (expression == null) {
        return null;
      }
    }
    // Indicates that we are inside console, not a lookup element!
    PydevConsoleReference consoleRef = PyUtil.as(expression.getReference(), PydevConsoleReference.class);
    if (consoleRef == null) { //shouldn't really happen!
      return null;
    }
    PyElement documentationElement = consoleRef.getDocumentationElement();
    if (documentationElement == null) {
      return null;
    }

    return new PyDocumentationBuilder(documentationElement, null).build();
  }

  @Override
  public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor,
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
