// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.console.completion.PydevConsoleElement;
import com.jetbrains.python.console.completion.PydevConsoleReference;
import com.jetbrains.python.documentation.PyDocumentationBuilder;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public class PydevDocumentationProvider extends AbstractDocumentationProvider {

  @Override
  public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, final Object object, final PsiElement element) {
    if (object instanceof PydevConsoleElement){
      return (PydevConsoleElement) object;
    }
    return super.getDocumentationElementForLookupItem(psiManager, object, element);
  }

  @Override
  public @Nls String generateDoc(final PsiElement element, @Nullable final PsiElement originalElement) {
    // Process PydevConsoleElement case
    if (element instanceof PydevConsoleElement){
      return PydevConsoleElement.generateDoc((PydevConsoleElement)element);
    }
    return null;
  }

  @Nullable
  public static @Nls String createDoc(final PsiElement element, final PsiElement originalElement) {
    final PyReferenceExpression expression = PsiTreeUtil.getNonStrictParentOfType(originalElement, PyReferenceExpression.class);
    // Indicates that we are inside console, not a lookup element!
    if (expression == null){
      return null;
    }
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
}
