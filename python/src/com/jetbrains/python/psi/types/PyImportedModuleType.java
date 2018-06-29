// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.PointInImport;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    final List<PsiElement> importedModuleCandidates = ResolveResultList.getElements(myImportedModule.multiResolve());
    final SmartList<RatedResolveResult> primaryResults = new SmartList<>();
    for (PsiElement moduleLocation : importedModuleCandidates) {
      final PsiFileSystemItem resolvedModuleOrPackage = PyUtil.as(moduleLocation, PsiFileSystemItem.class);
      if (resolvedModuleOrPackage != null) {
        final List<? extends RatedResolveResult> results =
          PyModuleType.resolveMemberInPackageOrModule(myImportedModule, resolvedModuleOrPackage, name, location, resolveContext);

        if (results != null) {
          primaryResults.addAll(results);
        }
      }
    }

    if (!primaryResults.isEmpty()) {
      return PyUtil.filterTopPriorityResults(primaryResults);
    }

    for (PsiElement moduleLocation : importedModuleCandidates) {
      final PsiFileSystemItem resolvedModuleOrPackage = PyUtil.as(moduleLocation, PsiFileSystemItem.class);
      if (resolvedModuleOrPackage == null) {
        continue;
      }
      //if it's a  synthetic import element
      if (myImportedModule.getImportElement() == null) {
        final PsiFile resolvingFromFile = location != null ? location.getContainingFile() : null;
        final List<RatedResolveResult> fallbackResults =
          ResolveImportUtil.resolveChildren(resolvedModuleOrPackage, name, resolvingFromFile, false, true,
                                            false, false);

        return ResolveResultList.asImportedResults(fallbackResults, null);
      }
    }
    return null;
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    final List<LookupElement> result = new ArrayList<>();
    for (PsiElement resolveResult : ResolveResultList.getElements(myImportedModule.multiResolve())) {
      if (resolveResult instanceof PyFile) {
        final PyModuleType moduleType = new PyModuleType((PyFile)resolveResult);
        final TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(location.getProject(), location.getContainingFile());

        result.addAll(moduleType.getCompletionVariantsAsLookupElements(location, context, false, false, typeEvalContext));
      }
      else if (resolveResult instanceof PsiDirectory) {
        final PsiDirectory dir = (PsiDirectory)resolveResult;
        if (PyUtil.isPackage(dir, location)) {
          if (ResolveImportUtil.getPointInImport(location) != PointInImport.NONE) {
            result.addAll(PyModuleType.getSubModuleVariants(dir, location, null));
          }
          else {
            result.addAll(PyModuleType.collectImportedSubmodulesAsLookupElements(dir, location, context.get(CTX_NAMES)));
          }
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PyImportedModuleType type = (PyImportedModuleType)o;
    return Objects.equals(myImportedModule, type.myImportedModule);
  }

  @Override
  public int hashCode() {

    return Objects.hash(myImportedModule);
  }
}
