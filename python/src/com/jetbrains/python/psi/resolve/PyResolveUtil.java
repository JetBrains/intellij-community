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
package com.jetbrains.python.psi.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 *
 * TODO: Merge it with {@link ScopeUtil}
 */
public class PyResolveUtil {
  private PyResolveUtil() {
  }

  /**
   * Crawls up scopes of the PSI tree, checking named elements and name definers.
   */
  public static void scopeCrawlUp(@NotNull PsiScopeProcessor processor, @NotNull PsiElement element, @Nullable String name,
                                  @Nullable PsiElement roof) {
    // Use real context here to enable correct completion and resolve in case of PyExpressionCodeFragment!!!
    final PsiElement realContext = PyPsiUtils.getRealContext(element);
    final ScopeOwner originalOwner;
    if (realContext != element && realContext instanceof PyFile) {
      originalOwner = (PyFile)realContext;
    }
    else {
      originalOwner = ScopeUtil.getScopeOwner(realContext);
    }
    final PsiElement parent = element.getParent();
    final boolean isGlobalOrNonlocal = parent instanceof PyGlobalStatement || parent instanceof PyNonlocalStatement;
    ScopeOwner owner = originalOwner;
    if (isGlobalOrNonlocal) {
      final ScopeOwner outerScopeOwner = ScopeUtil.getScopeOwner(owner);
      if (outerScopeOwner != null) {
        owner = outerScopeOwner;
      }
    }
    scopeCrawlUp(processor, owner, originalOwner, name, roof);
  }

  public static void scopeCrawlUp(@NotNull PsiScopeProcessor processor, @NotNull ScopeOwner scopeOwner, @Nullable String name,
                                  @Nullable PsiElement roof) {
    scopeCrawlUp(processor, scopeOwner, scopeOwner, name, roof);
  }

  public static void scopeCrawlUp(@NotNull PsiScopeProcessor processor, @Nullable ScopeOwner scopeOwner,
                                  @Nullable ScopeOwner originalScopeOwner, @Nullable String name, @Nullable PsiElement roof) {
    while (scopeOwner != null) {
      if (!(scopeOwner instanceof PyClass) || scopeOwner == originalScopeOwner) {
        final Scope scope = ControlFlowCache.getScope(scopeOwner);
        if (name != null) {
          final boolean includeNestedGlobals = scopeOwner instanceof PyFile;
          for (PsiNamedElement resolved : scope.getNamedElements(name, includeNestedGlobals)) {
            if (!processor.execute(resolved, ResolveState.initial())) {
              return;
            }
          }
        }
        else {
          for (PsiNamedElement element : scope.getNamedElements()) {
            if (!processor.execute(element, ResolveState.initial())) {
              return;
            }
          }
        }
        for (PyImportedNameDefiner definer : scope.getImportedNameDefiners()) {
          if (!processor.execute(definer, ResolveState.initial())) {
            return;
          }
        }
      }
      if (scopeOwner == roof) {
        return;
      }
      scopeOwner = ScopeUtil.getScopeOwner(scopeOwner);
    }
  }
}
