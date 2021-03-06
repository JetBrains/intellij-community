// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.findUsages;

import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.magicLiteral.PyMagicLiteralExtensionPoint;
import com.jetbrains.python.magicLiteral.PyMagicLiteralTools;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * TODO: Create strategies instead of chain of instanceof
 *
 * @author yole
 */
public class PythonFindUsagesProvider implements FindUsagesProvider {
  @Override
  public boolean canFindUsagesFor(@NotNull final PsiElement psiElement) {
    if (PyMagicLiteralTools.couldBeMagicLiteral(psiElement)) {
      return true;
    }
    return (psiElement instanceof PsiNamedElement) || (psiElement instanceof PyReferenceExpression);
  }

  @Override
  public String getHelpId(@NotNull PsiElement psiElement) {
    if (psiElement instanceof PyClass) {
      return "reference.dialogs.findUsages.class";
    }
    if (psiElement instanceof PyFunction) {
      return "reference.dialogs.findUsages.method";
    }
    if (psiElement instanceof PyReferenceExpression || psiElement instanceof PyTargetExpression || psiElement instanceof PyParameter) {
      return "reference.dialogs.findUsages.variable";
    }
    return null;
  }

  @Override
  @NotNull
  public String getType(@NotNull PsiElement element) {
    String literalString = tryFindMagicLiteralString(element, false);
    if (literalString != null) {
      return literalString;
    }

    if (element instanceof PyNamedParameter) return PyPsiBundle.message("find.usages.parameter");
    if (element instanceof PyFunction) {
      if (((PyFunction)element).getContainingClass() != null) {
        return PyPsiBundle.message("find.usages.method");
      }
      return PyPsiBundle.message("find.usages.function");
    }
    if (element instanceof PyClass) return PyPsiBundle.message("find.usages.class");
    if (element instanceof PyReferenceExpression) return PyPsiBundle.message("find.usages.variable");
    if (element instanceof PyTargetExpression) {
      final PyImportElement importElement = PsiTreeUtil.getParentOfType(element, PyImportElement.class);
      if (importElement != null && importElement.getAsNameElement() == element) {
        return PyPsiBundle.message("find.usages.imported.module.alias");
      }
      return PyPsiBundle.message("find.usages.variable");
    }
    if (element instanceof PyKeywordArgument) {
      return PyPsiBundle.message("find.usages.keyword.argument");
    }
    return "";
  }

  @Override
  @NotNull
  public String getDescriptiveName(@NotNull PsiElement element) {
    String literalString = tryFindMagicLiteralString(element, true);
    if (literalString != null) {
      return literalString;
    }

    if (element instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)element).getName();
      return name == null ? PyPsiBundle.message("find.usages.unnamed") : name;
    }
    if (element instanceof PyReferenceExpression) {
      String referencedName = ((PyReferenceExpression)element).getReferencedName();
      if (referencedName == null) {
        return PyPsiBundle.message("find.usages.unnamed");
      }
      return referencedName;
    }
    return "";
  }

  @Override
  @NotNull
  public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    return getDescriptiveName(element);
  }

  @Override
  public WordsScanner getWordsScanner() {
    return new PyWordsScanner();
  }


  /**
   * Finds text to display to user for element if element is magic literal
   * @param element element to check
   * @param obtainValue display element value (will display element type otherwise)
   * @return text (if found) or null
   */
  @Nullable
  @NlsSafe
  private static String tryFindMagicLiteralString(@NotNull final PsiElement element, final boolean obtainValue) {
    if (element instanceof PyStringLiteralExpression) {
      final PyMagicLiteralExtensionPoint point = PyMagicLiteralTools.getPoint((PyStringLiteralExpression)element);
      if (point != null) {
        if (obtainValue) {
          return ((StringLiteralExpression)element).getStringValue();
        }
        else {
          return point.getLiteralType();
        }
      }
    }
    return null;
  }
}
