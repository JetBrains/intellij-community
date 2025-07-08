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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
import com.jetbrains.python.psi.search.PySearchUtilBase;
import com.jetbrains.python.psi.stubs.PyClassAttributesIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiFile;
import com.jetbrains.python.pyi.PyiUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

/**
 * TODO: Merge it with {@link ScopeUtil}
 */
public final class PyResolveUtil {
  private PyResolveUtil() {
  }

  /**
   * Crawls up scopes of the PSI tree, checking named elements and name definers.
   */
  public static void scopeCrawlUp(@NotNull PsiScopeProcessor processor, @NotNull PsiElement element, @Nullable String name,
                                  @Nullable PsiElement roof) {
    ScopeOwner originalOwner = ScopeUtil.getScopeOwner(element);
    PsiElement parent = element.getParent();
    ScopeOwner owner = originalOwner;
    if (parent instanceof PyNonlocalStatement) {
      /* we need to search in one step out scope for nonlocal statements */
      if (owner == roof) {
        return;
      }
      owner = ScopeUtil.getScopeOwner(owner);
      if (owner == null) {
        return;
      }
    }
    else if (parent instanceof PyGlobalStatement) {
      /* we need to search directly in the global scope of the module for global statements */
      final PsiFile globalScope = element.getContainingFile();
      if (globalScope instanceof PyFile) {
        owner = (PyFile)globalScope;
      }
    }
    if (owner instanceof PyFunction function && function.getTypeParameterList() != null) {
      // Type parameters of generic functions and methods need to be visible in their parameter and return type annotations,
      // so we resolve names in these annotations starting from the containing function's scope, "lowering" their scope in
      // ScopeUtil.getScopeOwner().
      // At the same time, these annotations still need to see names from the class scope, such as nested classes defined there,
      // as if these names were evaluated on a class-level scope.
      PyAnnotation annotation = PsiTreeUtil.getParentOfType(element, PyAnnotation.class);
      if (PsiTreeUtil.getParentOfType(annotation, PyFunction.class, true, PyStatement.class) == function) {
        originalOwner = ScopeUtil.getScopeOwner(function);
      }
    }
    if (owner instanceof PyClass pyClass && pyClass.getTypeParameterList() != null) {
      // The same logic applies to the list of base classes of a generic class
      PyArgumentList superclassList = PsiTreeUtil.getParentOfType(element, PyArgumentList.class);
      if (superclassList != null && superclassList.getParent() == pyClass) {
        originalOwner = ScopeUtil.getScopeOwner(pyClass);
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
      final Scope scope = ControlFlowCache.getScope(scopeOwner);
      final Collection<PsiNamedElement> namedElements;
      if (name != null) {
        final boolean includeNestedGlobals = scopeOwner instanceof PyFile;
        namedElements = scope.getNamedElements(name, includeNestedGlobals);
      }
      else {
        namedElements = scope.getNamedElements();
      }
      for (PsiElement element : ContainerUtil.concat(namedElements, scope.getImportedNameDefiners())) {
        if (isClassLevelDefinitionInvisibleToReference(element, scopeOwner, originalScopeOwner)) continue;
        if (!processor.execute(element, ResolveState.initial())) {
          return;
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

  private static boolean isClassLevelDefinitionInvisibleToReference(@NotNull PsiElement definition,
                                                                    @NotNull ScopeOwner definitionScope,
                                                                    @Nullable ScopeOwner referenceScope) {
    return definitionScope instanceof PyClass pyClass && pyClass != referenceScope && !(definition instanceof PyTypeParameter);
  }

  /**
   * Resolves the passed expression in its containing file.
   * Does not go outside this file.
   */
  public static @NotNull Collection<PsiElement> resolveLocally(@NotNull PyReferenceExpression referenceExpression) {
    final String referenceName = referenceExpression.getName();

    if (referenceName == null || referenceExpression.isQualified()) {
      return Collections.emptyList();
    }

    final PyResolveProcessor processor = new PyResolveProcessor(referenceName, true);
    scopeCrawlUp(processor, referenceExpression, referenceName, null);

    return processor.getElements();
  }

  /**
   * Resolves the passed name in its containing file starting from the passed scope.
   * Does not go outside this file.
   */
  public static @NotNull Collection<PsiElement> resolveLocally(@NotNull ScopeOwner scopeOwner, @NotNull String name) {
    final PyResolveProcessor processor = new PyResolveProcessor(name, true);
    scopeCrawlUp(processor, scopeOwner, name, null);

    return processor.getElements();
  }

  /**
   * If the passed expression resolves to import elements, returns their qualified names.
   * Does not go outside the file containing this expression.
   */
  public static @NotNull List<QualifiedName> resolveImportedElementQNameLocally(@NotNull PyReferenceExpression expression) {
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
      List<QualifiedName> result = fullMultiResolveLocally(expression, new HashSet<>())
        .select(PyImportElement.class)
        .map(PyResolveUtil::getImportedElementQName)
        .nonNull()
        .toList();
      if (!result.isEmpty()) return result;
      if (expression.getName() == null) return result;

      PsiFile containingFile = expression.getContainingFile();
      if (!(containingFile instanceof PyFile)) return result;
      List<PyFromImportStatement> fromImports = ((PyFile)containingFile).getFromImports();
      return StreamEx.of(fromImports)
        .filter(it -> it.isStarImport())
        .map(it -> it.getImportSourceQName())
        .nonNull()
        .map(it -> it.append(expression.getName()))
        .toList();
    }
  }

  private static @Nullable QualifiedName getImportedElementQName(@NotNull PyImportElement element) {
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
   * Resolve a symbol by its qualified name, climbing up from the specified scope until the first component
   * of the qualified is resolved and then following the chain of type members.
   * <p> 
   * This type of resolve is stub-safe, i.e. it's not supposed to cause any un-stubbing of external files unless it is explicitly
   * allowed by the given type evaluation context.
   *
   * @param qualifiedName name of a symbol to resolve
   * @param scopeOwner    scope which serves as a starting point for resolve
   * @return all possible candidates that can be found by the given qualified name
   */
  public static @NotNull List<PsiElement> resolveQualifiedNameInScope(@NotNull QualifiedName qualifiedName,
                                                                      @NotNull ScopeOwner scopeOwner,
                                                                      @NotNull TypeEvalContext context) {
    return PyUtil.getParameterizedCachedValue(scopeOwner, Pair.create(qualifiedName, context), (param) -> {
      return doResolveQualifiedNameInScope(param.getFirst(), scopeOwner, param.getSecond());
    });
  }

  private static @NotNull List<PsiElement> doResolveQualifiedNameInScope(@NotNull QualifiedName qualifiedName,
                                                                         @NotNull ScopeOwner scopeOwner,
                                                                         @NotNull TypeEvalContext context) {
    final String firstName = qualifiedName.getFirstComponent();
    if (firstName == null || !(scopeOwner instanceof PyTypedElement)) return Collections.emptyList();

    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);

    List<RatedResolveResult> unqualifiedResults = new ArrayList<>();
    ScopeOwner curScope = scopeOwner;
    // Ideally, we should do `while (curScope != null && unqualifiedResults.isEmpty())` but
    // because of flow insensitivity it will break down for cases like the following in 
    // flask_sqlalchemy/__init__.pyi:
    // 
    // from .model import DefaultMeta as DefaultMeta, Model as Model
    //
    // class SQLAlchemy:
    //    Model: Model  # Model in the type hints resolves to its own target expression
    while (curScope != null) {
      unqualifiedResults.addAll(resolveShortNameInSingleScope(curScope, firstName, resolveContext));
      curScope = ScopeUtil.getScopeOwner(curScope);
    }

    if (unqualifiedResults.isEmpty()) {
      final PsiElement builtin = PyBuiltinCache.getInstance(scopeOwner).getByName(firstName);
      if (builtin == null) {
        return Collections.emptyList();
      }
      unqualifiedResults.add(new RatedResolveResult(RatedResolveResult.RATE_NORMAL, builtin));
    }

    final List<String> remainingNames = qualifiedName.removeHead(1).getComponents();
    final List<RatedResolveResult> result = StreamEx.of(remainingNames).foldLeft(StreamEx.of(unqualifiedResults), (prev, name) ->
      prev
        .map(RatedResolveResult::getElement)
        .select(PyTypedElement.class)
        .map(context::getType)
        .nonNull()
        .flatMap(type -> {
          // An instance type has access to instance attributes defined in __init__, a class type does not.
          final PyType instanceType = type instanceof PyClassLikeType ? ((PyClassLikeType)type).toInstance() : type;
          final List<? extends RatedResolveResult> results = instanceType.resolveMember(name, null, AccessDirection.READ, resolveContext);
          return results != null ? StreamEx.of(results) : StreamEx.<RatedResolveResult>empty();
        }))
      .toList();

    return PyUtil.filterTopPriorityElements(result);
  }

  private static @NotNull List<? extends RatedResolveResult> resolveShortNameInSingleScope(@NotNull ScopeOwner scopeOwner,
                                                                                           @NotNull String name,
                                                                                           @NotNull PyResolveContext resolveContext) {
    if (scopeOwner instanceof PyiFile fileScope) {
      // pyi-stubs are special cased because
      // `resolveMember` delegates to `multiResolveName(..., true)` and
      // it skips elements that are imported without `as`
      return fileScope.multiResolveName(name, false);
    }
    else if (scopeOwner instanceof PyFunction functionScope) {
      final Stream<PsiNamedElement> targets = StreamEx
        .of(PsiTreeUtil.getStubChildrenOfTypeAsList(scopeOwner, PyTargetExpression.class))
        .filter(it -> !it.isQualified())
        .select(PsiNamedElement.class);

      final Stream<PsiNamedElement> parameters = StreamEx
        .of(functionScope.getParameterList().getParameters())
        .select(PsiNamedElement.class);

      return StreamEx
        .of(targets)
        .append(parameters)
        .filter(it -> name.equals(it.getName()))
        .map(it -> new RatedResolveResult(RatedResolveResult.RATE_NORMAL, it))
        .append(resolveTypeParameters(functionScope, name))
        .toList();
    }
    else if (scopeOwner instanceof PyTypeAliasStatement) {
      return resolveTypeParameters((PyTypeParameterListOwner)scopeOwner, name);
    }
    else {
      final PyType scopeType = resolveContext.getTypeEvalContext().getType((PyTypedElement)scopeOwner);
      if (scopeType == null) return Collections.emptyList();
      List<? extends RatedResolveResult> typeMembers = scopeType.resolveMember(name, null, AccessDirection.READ, resolveContext);
      if (scopeOwner instanceof PyClass pyClass) {
        return ContainerUtil.concat(ContainerUtil.notNullize(typeMembers), resolveTypeParameters(pyClass, name));
      }
      else {
        return ContainerUtil.notNullize(typeMembers);
      }
    }
  }

  public static @Nullable String resolveStrArgument(@NotNull PyCallExpression callExpression, int index, @NotNull String keyword) {
    // SUPPORTED CASES:

    // name = "Point"
    // Point = namedtuple(name, ...)

    // Point = namedtuple("Point", ...)

    // Point = namedtuple(("Point"), ...)

    // Point = namedtuple(typename="Point", ...)

    PyExpression argument = callExpression.getArgument(index, keyword, PyExpression.class);
    return resolveStrArgument(argument);
  }

  public static @Nullable String resolveStrArgument(@Nullable PyExpression argument) {
    final PyExpression expression = PyPsiUtils.flattenParens(argument);

    if (expression instanceof PyReferenceExpression) {
      return PyPsiUtils.strValue(fullResolveLocally((PyReferenceExpression)expression));
    }

    return PyPsiUtils.strValue(expression);
  }

  /**
   * Follows one of 'target-reference` chain and returns assigned value or null.
   * Does not go outside the file containing the passed expression.
   *
   * @param referenceExpression expression to resolve
   * @return resolved assigned value.
   */
  public static @Nullable PyExpression fullResolveLocally(@NotNull PyReferenceExpression referenceExpression) {
    return fullMultiResolveLocally(referenceExpression, new HashSet<>()).select(PyExpression.class).findFirst().orElse(null);
  }

  /**
   * Runs DFS on assignment chains and returns all reached assigned values.
   * Does not go outside the file containing the passed expression.
   *
   * @param referenceExpression expression to resolve
   * @param visited             set to store visited references to prevent recursion
   * @return resolved assigned values.
   * <i>Note: the returned stream could contain null values.</i>
   */
  private static @NotNull StreamEx<PsiElement> fullMultiResolveLocally(@NotNull PyReferenceExpression referenceExpression,
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
    // Allow forward references in Pyi files
    if (PyiUtil.isInsideStub(element)) {
      return true;
    }
    // Forward references are allowed in annotations according to PEP 563
    PsiFile file = element.getContainingFile();
    if (file instanceof PyFile pyFile) {
      boolean isAnnotation = pyFile.getLanguageLevel().isAtLeast(LanguageLevel.PYTHON37) &&
                             pyFile.hasImportFromFuture(FutureFeature.ANNOTATIONS) &&
                             PsiTreeUtil.getParentOfType(element, PyAnnotation.class) != null;
      if (isAnnotation) return true;

      boolean isReferenceFromTypeParameterList = pyFile.getLanguageLevel().isAtLeast(LanguageLevel.PYTHON312) &&
                                                 PsiTreeUtil.getParentOfType(element, PyTypeParameter.class, true) != null;
      if (isReferenceFromTypeParameterList) return true;
    }
    return false;
  }

  public static @Nullable ScopeOwner parentScopeForUnresolvedClassLevelName(@NotNull PyClass cls, @NotNull String name) {
    // com.jetbrains.python.codeInsight.dataflow.scope.Scope.containsDeclaration could not be used
    // because it runs resolve on imports that is forbidden during indexing
    return containsDeclaration(cls, name) ? PyUtil.as(cls.getContainingFile(), PyFile.class) : ScopeUtil.getScopeOwner(cls);
  }

  private static boolean containsDeclaration(@NotNull PyClass cls, @NotNull String name) {
    final Scope scope = ControlFlowCache.getScope(cls);

    if (!scope.getNamedElements(name, false).isEmpty()) return true;

    return StreamEx
      .of(scope.getImportedNameDefiners())
      .select(PyImportElement.class)
      .anyMatch(e -> name.equals(e.getVisibleName()));
  }

  public static void addImplicitResolveResults(@NotNull String referencedName,
                                               @NotNull ResolveResultList ret,
                                               @NotNull PyQualifiedExpression element) {
    final Project project = element.getProject();
    final GlobalSearchScope scope = PySearchUtilBase.excludeSdkTestsScope(project);
    final Collection<PyFunction> functions = PyFunctionNameIndex.find(referencedName, project, scope);
    final PsiFile containingFile = element.getContainingFile();
    final List<QualifiedName> imports;
    if (containingFile instanceof PyFile) {
      imports = collectImports((PyFile)containingFile);
    }
    else {
      imports = Collections.emptyList();
    }
    for (PyFunction function : functions) {
      if (function.getContainingClass() != null) {
        ret.add(new ImplicitResolveResult(function, getImplicitResultRate(function, imports, element)));
      }
    }

    PyClassAttributesIndex
      .findClassAndInstanceAttributes(referencedName, project, scope)
      .forEach(attribute -> ret.add(new ImplicitResolveResult(attribute, getImplicitResultRate(attribute, imports, element))));
  }

  private static List<QualifiedName> collectImports(PyFile containingFile) {
    List<QualifiedName> imports = new ArrayList<>();
    for (PyFromImportStatement anImport : containingFile.getFromImports()) {
      final QualifiedName source = anImport.getImportSourceQName();
      if (source != null) {
        imports.add(source);
      }
    }
    for (PyImportElement importElement : containingFile.getImportTargets()) {
      final QualifiedName qName = importElement.getImportedQName();
      if (qName != null) {
        imports.add(qName.removeLastComponent());
      }
    }
    return imports;
  }

  private static int getImplicitResultRate(PyElement target, List<QualifiedName> imports, PyQualifiedExpression element) {
    int rate = RatedResolveResult.RATE_LOW;
    if (target.getContainingFile() == element.getContainingFile()) {
      rate += 200;
    }
    else {
      final VirtualFile vFile = target.getContainingFile().getVirtualFile();
      if (vFile != null) {
        if (ProjectScope.getProjectScope(element.getProject()).contains(vFile)) {
          rate += 80;
        }
        final QualifiedName qName = QualifiedNameFinder.findShortestImportableQName(element, vFile);
        if (qName != null && imports.contains(qName)) {
          rate += 70;
        }
      }
    }
    if (element.getParent() instanceof PyCallExpression) {
      if (target instanceof PyFunction) rate += 50;
    }
    else {
      if (!(target instanceof PyFunction)) rate += 50;
    }
    return rate;
  }

  public static @NotNull List<@NotNull PsiElement> multiResolveDeclaration(@NotNull PsiReference reference, @NotNull PyResolveContext resolveContext) {
    final PsiElement element = reference.getElement();

    final var context = resolveContext.getTypeEvalContext();
    final var call = context.maySwitchToAST(element) ? PyCallExpressionNavigator.getPyCallExpressionByCallee(element) : null;
    if (call != null && element instanceof PyTypedElement) {
      final var type = PyUtil.as(context.getType((PyTypedElement)element), PyClassType.class);

      if (type != null && type.isDefinition()) {
        final var cls = type.getPyClass();

        final var constructor = ContainerUtil.find(
          PyUtil.filterTopPriorityElements(PyCallExpressionHelper.resolveImplicitlyInvokedMethods(type, call, resolveContext)),
          it -> it instanceof PyPossibleClassMember possibleClassMember && possibleClassMember.getContainingClass() == cls
        );

        if (constructor != null) {
          return List.of(constructor);
        }
      }
    }

    if (reference instanceof PsiPolyVariantReference multiReference) {
      return PyUtil.multiResolveTopPriority(multiReference);
    }
    final var result = reference.resolve();
    if (result == null) return List.of();
    return List.of(result);
  }

  public static @Nullable PsiElement resolveDeclaration(@NotNull PsiReference reference, @NotNull PyResolveContext resolveContext) {
    final var result = multiResolveDeclaration(reference, resolveContext);
    if (result.isEmpty()) return null;
    return result.get(0);
  }

  private static @NotNull List<RatedResolveResult> resolveTypeParameters(@NotNull PyTypeParameterListOwner typeParameterListOwner,
                                                                         @NotNull String name) {
    if (typeParameterListOwner.getTypeParameterList() != null) {
      return StreamEx.of(typeParameterListOwner.getTypeParameterList().getTypeParameters())
        .filter(it -> name.equals(it.getName()))
        .map(it -> new RatedResolveResult(RatedResolveResult.RATE_NORMAL, it))
        .toList();
    }
    else {
      return Collections.emptyList();
    }
  }
}
