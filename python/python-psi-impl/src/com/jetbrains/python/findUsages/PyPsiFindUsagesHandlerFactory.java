// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.FindUsagesHandlerBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


@ApiStatus.Internal
public interface PyPsiFindUsagesHandlerFactory {
  default boolean canFindUsages(@NotNull PsiElement element) {
    return element instanceof PyClass ||
           (element instanceof PyFile && PyUtil.isPackage((PyFile)element)) ||
           element instanceof PyImportedModule ||
           element instanceof PyFunction ||
           element instanceof PyTargetExpression;
  }

  default @Nullable FindUsagesHandlerBase createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    if (element instanceof PyImportedModule) {
      final PsiElement resolved = ((PyImportedModule)element).resolve();
      if (resolved != null) {
        element = resolved;
      }
    }
    if (element instanceof PsiFileSystemItem) {
      return createModuleFindUsagesHandler((PsiFileSystemItem)element);
    }
    if (element instanceof PyFunction) {
      if (!forHighlightUsages) {
        TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), null);
        final Collection<PsiElement> superMethods = PySuperMethodsSearch.search((PyFunction)element, true, context).findAll();
        if (!superMethods.isEmpty()) {
          final PsiElement next = superMethods.iterator().next();
          // TODO should do this for Jython functions overriding Java methods too
          if (next instanceof PyFunction && !isInObject((PyFunction)next)) {
            if (PythonUiService.getInstance().showYesDialog(element.getProject(), PyPsiBundle
                                                              .message("python.find.usages.base.method.question",
                                                                       ((PyFunction)element).getName(), ((PyFunction)next).getContainingClass().getName()),
                                                            PyPsiBundle.message("python.find.usages"))) {
              List<PsiElement> allMethods = new ArrayList<>();
              allMethods.add(element);
              allMethods.addAll(superMethods);
              return new PyFunctionFindUsagesHandler(element, allMethods);
            }
            else {
              return new PyFunctionFindUsagesHandler(element);
            }
          }
        }
      }
      return new PyFunctionFindUsagesHandler(element);
    }
    if (element instanceof PyClass) {
      return new PyClassFindUsagesHandler((PyClass)element);
    }
    if (element instanceof PyTargetExpression) {
      return new PyTargetExpressionFindUsagesHandler(((PyTargetExpression)element));
    }
    return null;
  }

  default @NotNull PyModuleFindUsagesHandler createModuleFindUsagesHandler(@NotNull PsiFileSystemItem element) {
    return new PyModuleFindUsagesHandler(element);
  }

  static boolean isInObject(PyFunction fun) {
    final PyClass containingClass = fun.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    return PyUtil.isObjectClass(containingClass);
  }
}
