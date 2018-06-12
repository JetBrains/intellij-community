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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
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
import com.jetbrains.python.pyi.PyiUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
      if (name != null && scopeOwner instanceof PyClass && scopeOwner == originalScopeOwner) {
        scopeOwner = parentScopeForUnresolvedClassLevelName((PyClass)scopeOwner, name);
      }
      else {
        scopeOwner = ScopeUtil.getScopeOwner(scopeOwner);
      }
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

  @NotNull
  public static List<QualifiedName> resolveImportedElementQNameLocally(@NotNull PyReferenceExpression expression) {
    // SUPPORTED CASES:

    // import six
    // six.with_metaclass(...)

    // from six import metaclass
    // with_metaclass(...)

    // from six import with_metaclass as w_m
    // w_m(...)

    final PyExpression qualifier = expression.getQualifier();
    if (qualifier instanceof PyReferenceExpression) {
      final String name = expression.getName();

      return name == null
             ? Collections.emptyList()
             : ContainerUtil.map(resolveImportedElementQNameLocally((PyReferenceExpression)qualifier), qn -> qn.append(name));
    }
    else {
      return fullMultiResolveLocally(expression, new HashSet<>())
        .select(PyImportElement.class)
        .map(PyResolveUtil::getImportedElementQName)
        .nonNull()
        .toList();
    }
  }

  @Nullable
  private static QualifiedName getImportedElementQName(@NotNull PyImportElement element) {
    final PyStatement importStatement = element.getContainingImportStatement();

    if (importStatement instanceof PyFromImportStatement) {
      final QualifiedName importSourceQName = ((PyFromImportStatement)importStatement).getImportSourceQName();
      final QualifiedName importedQName = element.getImportedQName();

      if (importSourceQName != null && importedQName != null) {
        return importSourceQName.append(importedQName);
      }
    }

    return element.getImportedQName();
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

  @Nullable
  public static String resolveFirstStrArgument(@NotNull PyCallExpression callExpression) {
    // SUPPORTED CASES:

    // name = "Point"
    // Point = namedtuple(name, ...)

    // Point = namedtuple("Point", ...)

    // Point = namedtuple(("Point"), ...)

    // name = "Point"
    // Point = NamedTuple(name, ...)

    // Point = NamedTuple("Point", ...)

    // Point = NamedTuple(("Point"), ...)

    final PyExpression nameExpression = PyPsiUtils.flattenParens(callExpression.getArgument(0, PyExpression.class));

    if (nameExpression instanceof PyReferenceExpression) {
      return PyPsiUtils.strValue(fullResolveLocally((PyReferenceExpression)nameExpression));
    }

    return PyPsiUtils.strValue(nameExpression);
  }

  /**
   * Follows one of 'target-reference` chain and returns assigned value or null.
   *
   * @param referenceExpression expression to resolve
   * @return resolved assigned value.
   */
  @Nullable
  public static PyExpression fullResolveLocally(@NotNull PyReferenceExpression referenceExpression) {
    return fullMultiResolveLocally(referenceExpression, new HashSet<>()).select(PyExpression.class).findFirst().orElse(null);
  }

  /**
   * Runs DFS on assignment chains and returns all reached assigned values.
   *
   * @param referenceExpression expression to resolve
   * @param visited             set to store visited references to prevent recursion
   * @return resolved assigned values.
   * <i>Note: the returned stream could contain null values.</i>
   */
  @NotNull
  private static StreamEx<PsiElement> fullMultiResolveLocally(@NotNull PyReferenceExpression referenceExpression,
                                                              @NotNull Set<PyReferenceExpression> visited) {
    return StreamEx
      .of(resolveLocally(referenceExpression))
      .flatMap(
        element -> {
          if (element instanceof PyTargetExpression) {
            final PyExpression assignedValue = ((PyTargetExpression)element).findAssignedValue();

            if (assignedValue instanceof PyReferenceExpression && visited.add((PyReferenceExpression)assignedValue)) {
              return fullMultiResolveLocally((PyReferenceExpression)assignedValue, visited);
            }

            return StreamEx.of(assignedValue);
          }

          return StreamEx.of(element);
        }
      );
  }

  /**
   * Check whether forward references are allowed for the given element.
   */
  public static boolean allowForwardReferences(@NotNull PyQualifiedExpression element) {
    // Allow forward references in Pyi annotations
    if (PyiUtil.isInsideStubAnnotation(element)) {
      return true;
    }
    // Forward references are allowed in annotations according to PEP 563
    PsiFile file = element.getContainingFile();
    if (file instanceof PyFile) {
      final PyFile pyFile = (PyFile)file;
      return pyFile.getLanguageLevel().isAtLeast(LanguageLevel.PYTHON37) &&
             pyFile.hasImportFromFuture(FutureFeature.ANNOTATIONS) &&
             PsiTreeUtil.getParentOfType(element, PyAnnotation.class) != null;
    }
    return false;
  }

  @Nullable
  public static ScopeOwner parentScopeForUnresolvedClassLevelName(@NotNull PyClass cls, @NotNull String name) {
    return ControlFlowCache.getScope(cls).containsDeclaration(name)
           ? PyUtil.as(cls.getContainingFile(), PyFile.class)
           : ScopeUtil.getScopeOwner(cls);
  }
}
