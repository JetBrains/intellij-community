/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

  @NotNull
  public static Collection<PsiElement> resolveLocally(@NotNull PyReferenceExpression referenceExpression) {
    final String referenceName = referenceExpression.getName();

    if (referenceName == null || referenceExpression.isQualified()) {
      return Collections.emptyList();
    }

    final PyResolveProcessor processor = new PyResolveProcessor(referenceName, true);
    scopeCrawlUp(processor, referenceExpression, referenceName, null);

    return processor.getElements();
  }

  @NotNull
  public static Collection<PsiElement> resolveLocally(@NotNull ScopeOwner scopeOwner, @NotNull String name) {
    final PyResolveProcessor processor = new PyResolveProcessor(name, true);
    scopeCrawlUp(processor, scopeOwner, name, null);

    return processor.getElements();
  }

  /**
   * Resolve a symbol by its qualified name, starting from the specified file and then following the chain of type members.
   * This type of resolve is stub-safe, i.e. it's not supposed to cause any un-stubbing of external files unless it explicitly
   * allowed by the given type evaluation context.
   *
   * @param qualifiedName name of a symbol to resolve
   * @param file          module which serves as a starting point for resolve
   * @return all possible candidates that can be found by the given qualified name
   */
  @NotNull
  public static List<PsiElement> resolveQualifiedNameInFile(@NotNull QualifiedName qualifiedName,
                                                            @NotNull PyFile file,
                                                            @NotNull TypeEvalContext context) {
    final String firstName = qualifiedName.getFirstComponent();
    if (firstName == null) {
      return Collections.emptyList();
    }
    final List<RatedResolveResult> unqualifiedResults = file.multiResolveName(firstName, false);
    final StreamEx<RatedResolveResult> initialResults;
    if (unqualifiedResults.isEmpty()) {
      final PsiElement builtin = PyBuiltinCache.getInstance(file).getByName(firstName);
      if (builtin == null) {
        return Collections.emptyList();
      }
      initialResults = StreamEx.of(new RatedResolveResult(RatedResolveResult.RATE_NORMAL, builtin));
    }
    else {
      initialResults = StreamEx.of(unqualifiedResults);
    }
    final List<String> remainingNames = qualifiedName.removeHead(1).getComponents();
    final StreamEx<RatedResolveResult> result = StreamEx.of(remainingNames).foldLeft(initialResults, (prev, name) ->
      prev
        .map(RatedResolveResult::getElement)
        .select(PyTypedElement.class)
        .map(context::getType)
        .nonNull()
        .flatMap(type -> {
          final PyType instanceType = type instanceof PyClassLikeType ? ((PyClassLikeType)type).toInstance() : type;
          final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
          final List<? extends RatedResolveResult> results = instanceType.resolveMember(name, null, AccessDirection.READ, resolveContext);
          return results != null ? StreamEx.of(results) : StreamEx.<RatedResolveResult>empty();
        }));
    return PyUtil.filterTopPriorityResults(result.toArray(RatedResolveResult[]::new));
  }
}
