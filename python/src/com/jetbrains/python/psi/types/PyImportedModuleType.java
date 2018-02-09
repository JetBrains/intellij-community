// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyImportedModuleType implements PyType {
  @NotNull private final PyImportedModule myImportedModule;

  public PyImportedModuleType(@NotNull PyImportedModule importedModule) {
    myImportedModule = importedModule;
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    final PsiElement resolved = myImportedModule.resolve();
    if (resolved != null) {
      final PsiFile containingFile = location != null ? location.getContainingFile() : null;
      PsiElement resolvedChild = ResolveImportUtil.resolveChild(resolved, name, containingFile, false, true,
                                                                false);
      final PyImportElement importElement = myImportedModule.getImportElement();
      final PyFile resolvedFile = PyUtil.as(resolved, PyFile.class);
      if (location != null && importElement != null && PyUtil.inSameFile(location, importElement) &&
          ResolveImportUtil.getPointInImport(location) == PointInImport.NONE && resolved instanceof PsiFileSystemItem &&
          (resolvedFile == null || !PyUtil.isPackage(resolvedFile) || resolvedFile.getElementNamed(name) == null)) {

        ResolveResultList res = new ResolveResultList();
        boolean isPackage =
          PyModuleType.processImportedSubmodules((PsiFileSystemItem)resolved, location, (nameDefiner, importedSubmodule) -> {
            if (importedSubmodule.equals(resolvedChild)) {
              res.add(new ImportedResolveResult(resolvedChild, RatedResolveResult.RATE_NORMAL, nameDefiner));
              return false;
            }
            return true;
          });

        if (isPackage) {
          return res;
        }
      }
      if (resolvedChild != null) {
        ResolveResultList l = new ResolveResultList();
        l.add(new ImportedResolveResult(resolvedChild, RatedResolveResult.RATE_NORMAL, importElement));
        return l;
      }
    }
    return null;
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    final List<LookupElement> result = new ArrayList<>();
    final PsiElement resolved = myImportedModule.resolve();
    if (resolved instanceof PyFile) {
      final PyModuleType moduleType = new PyModuleType((PyFile)resolved, myImportedModule);
      final TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(location.getProject(), location.getContainingFile());

      result.addAll(moduleType.getCompletionVariantsAsLookupElements(location, context, false, false, typeEvalContext));
    }
    else if (resolved instanceof PsiDirectory) {
      final PsiDirectory dir = (PsiDirectory)resolved;
      if (PyUtil.isPackage(dir, location)) {
        if (ResolveImportUtil.getPointInImport(location) != PointInImport.NONE) {
          result.addAll(PyModuleType.getSubModuleVariants(dir, location, null));
        }
        else {
          result.addAll(PyModuleType.collectImportedSubmodulesAsLookupElements(dir, location, context.get(CTX_NAMES)));
        }
      }
    }
    return ArrayUtil.toObjectArray(result);
  }

  @Override
  public String getName() {
    return "imported module " + myImportedModule.getImportedPrefix().toString();
  }

  @Override
  public boolean isBuiltin() {
    return false;  // no module can be imported from builtins
  }

  @Override
  public void assertValid(String message) {
  }

  @NotNull
  public PyImportedModule getImportedModule() {
    return myImportedModule;
  }

  @Override
  public void accept(@NotNull PyTypeVisitor visitor) {
    if (visitor instanceof PyTypeVisitorExt) {
      ((PyTypeVisitorExt)visitor).visitImportedModuleType(this);
    }
  }
}
