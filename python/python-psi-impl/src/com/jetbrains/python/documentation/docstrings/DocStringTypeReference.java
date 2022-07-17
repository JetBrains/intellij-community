// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.docstrings;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User : catherine
 */
public class DocStringTypeReference extends PsiPolyVariantReferenceBase<PsiElement> {
  @Nullable private PyType myType;
  @NotNull private final TextRange myFullRange;
  @Nullable private final PyImportElement myImportElement;

  public DocStringTypeReference(PsiElement element, TextRange range, @NotNull TextRange fullRange, @Nullable PyType type,
                                @Nullable PyImportElement importElement) {
    super(element, range);
    myFullRange = fullRange;
    myType = type;
    myImportElement = importElement;
  }

  @Nullable
  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element.equals(resolve())) {
      return element;
    }
    if (myElement instanceof PyStringLiteralExpression && element instanceof PyClass) {
      final PyStringLiteralExpression e = (PyStringLiteralExpression)myElement;
      final PyClass cls = (PyClass)element;
      QualifiedName qname = QualifiedNameFinder.findCanonicalImportPath(cls, element);
      if (qname != null) {
        qname = qname.append(cls.getName());
        myType = new PyClassTypeImpl(cls, false);
        return ElementManipulators.handleContentChange(e, myFullRange, qname.toString());
      }
    }
    return null;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    newElementName = StringUtil.trimEnd(newElementName, PyNames.DOT_PY);
    return super.handleElementRename(newElementName);
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    if (myType instanceof PyImportedModuleType) {
      return element.equals(PyUtil.turnInitIntoDir(resolve()));
    }
    return super.isReferenceTo(element);
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    PsiElement result = null;
    final ResolveResultList results = new ResolveResultList();
    if (myType instanceof PyClassType) {
      result = ((PyClassType)myType).getPyClass();
    }
    else if (myType instanceof PyImportedModuleType) {
      result = ((PyImportedModuleType)myType).getImportedModule().resolve();
    }
    else if (myType instanceof PyModuleType) {
      result = ((PyModuleType)myType).getModule();
    }
    if (result != null) {
      if (myImportElement != null) {
        results.add(new ImportedResolveResult(result, RatedResolveResult.RATE_NORMAL, myImportElement));
      }
      else {
        results.poke(result, RatedResolveResult.RATE_NORMAL);
      }
    }
    return results.toArray(ResolveResult.EMPTY_ARRAY);
  }

  @Override
  public Object @NotNull [] getVariants() {
    // see PyDocstringCompletionContributor
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @NotNull
  public List<Object> collectTypeVariants() {
    final PsiFile file = myElement.getContainingFile();
    final List<Object> variants =
      Lists.newArrayList(PyNames.TYPE_STR, PyNames.TYPE_INT, "basestring", "bool", "buffer", "bytearray", "complex", "dict",
                         "tuple", "enumerate", "file", "float", "frozenset", "list", PyNames.TYPE_LONG, "set", "object");
    if (file instanceof PyFile) {
      variants.addAll(((PyFile)file).getTopLevelClasses());
      final List<PyFromImportStatement> fromImports = ((PyFile)file).getFromImports();
      for (PyFromImportStatement fromImportStatement : fromImports) {
        final PyImportElement[] elements = fromImportStatement.getImportElements();
        for (PyImportElement element : elements) {
          final PyReferenceExpression referenceExpression = element.getImportReferenceExpression();
          if (referenceExpression == null) continue;
          final PyType type = TypeEvalContext.userInitiated(file.getProject(), CompletionUtilCoreImpl
            .getOriginalOrSelf(file)).getType(referenceExpression);
          if (type instanceof PyClassType) {
            variants.add(((PyClassType)type).getPyClass());
          }
        }
      }
    }
    return variants;
  }
}
