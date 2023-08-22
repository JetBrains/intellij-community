// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.references;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtilRt;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.psi.PyUtil.as;


public class PyTargetReference extends PyReferenceImpl {
  public PyTargetReference(PyQualifiedExpression element, PyResolveContext context) {
    super(element, context);
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    if (myElement instanceof StubBasedPsiElementBase && ((StubBasedPsiElementBase<?>)myElement).getStub() == null ||
        myContext.getTypeEvalContext().maySwitchToAST(myElement)) {
      final ResolveResult[] results = super.multiResolve(incompleteCode);
      boolean shadowed = false;
      for (ResolveResult result : results) {
        final PsiElement element = result.getElement();
        if (element != null && (element.getContainingFile() != myElement.getContainingFile() ||
                                element instanceof PyFunction || element instanceof PyClass)) {
          shadowed = true;
          break;
        }
      }
      if (results.length > 0 && !shadowed) {
        return results;
      }
    }
    // resolve to self if no other target found
    return new ResolveResult[] { new PsiElementResolveResult(myElement) };
  }

  @Override
  public Object @NotNull [] getVariants() {
    final PyImportElement importElement = PsiTreeUtil.getParentOfType(myElement, PyImportElement.class);
    // reference completion is useless in 'as' part of import statement (PY-2384)
    if (importElement != null && myElement == importElement.getAsNameElement()) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }
    PyClassPattern classPattern = getContainingClassPatternIfInAttributeNamePosition();
    if (classPattern != null) {
      TypeEvalContext context = TypeEvalContext.codeCompletion(myElement.getProject(), myElement.getContainingFile());
      return PyKeywordPatternReference.collectClassAttributeVariants(myElement, classPattern, context);
    }
    return super.getVariants();
  }

  @Nullable
  private PyClassPattern getContainingClassPatternIfInAttributeNamePosition() {
    PsiElement grandParent = myElement.getParent().getParent();
    if (myElement.getParent() instanceof PyCapturePattern && grandParent instanceof PyPatternArgumentList) {
      return as(grandParent.getParent(), PyClassPattern.class);
    }
    return null;
  }
}
