/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.types;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.resolve.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyImportedModuleType implements PyType {
  @NotNull private PyImportedModule myImportedModule;

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
    if (resolved instanceof PyFile) {
      final PyFile file = (PyFile)resolved;
      return new PyModuleType(file, myImportedModule).resolveMember(name, location, direction, resolveContext);
    }
    else if (resolved instanceof PsiDirectory) {
      final PsiElement element = ResolveImportUtil.resolveChild(resolved, name, null, true, true);
      if (location != null && ResolveImportUtil.getPointInImport(location) == PointInImport.NONE) {
        final List<ImportedResolveResult> imported = PyModuleType.collectImportedSubmodules((PsiDirectory)resolved, location);
        return ContainerUtil.mapNotNull(imported, new Function<ImportedResolveResult, RatedResolveResult>() {
          @Override
          public RatedResolveResult fun(ImportedResolveResult result) {
            return result.getElement() == element ? result : null;
          }
        });
      }
      return ResolveImportUtil.rateResults(Collections.singletonList(element));
    }
    return null;
  }

  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    final List<LookupElement> result = new ArrayList<LookupElement>();
    final PsiElement resolved = myImportedModule.resolve();
    if (resolved instanceof PyFile) {
      final PyModuleType moduleType = new PyModuleType((PyFile)resolved, myImportedModule);
      result.addAll(moduleType.getCompletionVariantsAsLookupElements(location, context, false, false));
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
}
