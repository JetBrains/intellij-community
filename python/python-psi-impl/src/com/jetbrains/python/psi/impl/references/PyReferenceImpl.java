// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.references;

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiFile;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.refactoring.PyDefUseUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;


public class PyReferenceImpl implements PsiReferenceEx, PsiPolyVariantReference {
  protected final PyQualifiedExpression myElement;
  protected final PyResolveContext myContext;

  public PyReferenceImpl(PyQualifiedExpression element, @NotNull PyResolveContext context) {
    myElement = element;
    myContext = context;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    final ASTNode nameElement = myElement.getNameElement();
    return nameElement != null ? nameElement.getPsi().getTextRangeInParent() : TextRange.from(0, myElement.getTextLength());
  }

  @Override
  public @NotNull PsiElement getElement() {
    return myElement;
  }

  /**
   * Resolves reference to the most obvious point.
   * Imported module names: to module file (or directory for a qualifier).
   * Other identifiers: to most recent definition before this reference.
   * This implementation is cached.
   *
   * @see #resolveInner().
   */
  @Override
  public @Nullable PsiElement resolve() {
    final ResolveResult[] results = multiResolve(false);
    return results.length >= 1 && !(results[0] instanceof ImplicitResolveResult) ? results[0].getElement() : null;
  }

  // it is *not* final so that it can be changed in debug time. if set to false, caching is off
  private static final boolean USE_CACHE = true;

  /**
   * Resolves reference to possible referred elements.
   * First element is always what resolve() would return.
   * Imported module names: to module file, or {directory, '__init__.py}' for a qualifier.
   * todo Local identifiers: a list of definitions in the most recent compound statement
   * (e.g. {@code if X: a = 1; else: a = 2} has two definitions of {@code a}.).
   * todo Identifiers not found locally: similar definitions in imported files and builtins.
   *
   * @see PsiPolyVariantReference#multiResolve(boolean)
   */
  @Override
  public ResolveResult @NotNull [] multiResolve(final boolean incompleteCode) {
    if (USE_CACHE) {
      final ResolveCache cache = ResolveCache.getInstance(getElement().getProject());
      final boolean actuallyIncomplete = incompleteCode || myContext.getTypeEvalContext().hasAssumptions();
      return cache.resolveWithCaching(this, CachingResolver.INSTANCE, true, actuallyIncomplete);
    }
    else {
      return multiResolveInner();
    }
  }

  // sorts and modifies results of resolveInner

  private ResolveResult @NotNull [] multiResolveInner() {
    final String referencedName = myElement.getReferencedName();
    if (referencedName == null) return ResolveResult.EMPTY_ARRAY;

    final List<RatedResolveResult> targets = resolveInner();
    if (targets.isEmpty()) return ResolveResult.EMPTY_ARRAY;

    return RatedResolveResult.sorted(targets).toArray(ResolveResult.EMPTY_ARRAY);
  }

  private static @NotNull ResolveResultList resolveToLatestDefs(@NotNull List<Instruction> instructions,
                                                                @NotNull PsiElement element,
                                                                @NotNull String name,
                                                                @NotNull TypeEvalContext context) {
    final ResolveResultList ret = new ResolveResultList();
    for (Instruction instruction : instructions) {
      final PsiElement definition = instruction.getElement();
      // TODO: This check may slow down resolving, but it is the current solution to the comprehension scopes problem
      if (isInnerComprehension(element, definition)) continue;
      if (definition instanceof PyImportedNameDefiner definer && !(definition instanceof PsiNamedElement)) {
        final List<RatedResolveResult> resolvedResults = definer.multiResolveName(name);
        for (RatedResolveResult result : resolvedResults) {
          ret.add(new ImportedResolveResult(result.getElement(), result.getRate(), definer));
        }
        if (resolvedResults.isEmpty()) {
          ret.add(new ImportedResolveResult(null, RatedResolveResult.RATE_NORMAL, definer));
        }
        else {
          // TODO this kind of resolve contract is quite stupid
          ret.poke(definer, RatedResolveResult.RATE_LOW);
        }
      }
      else {
        ret.poke(definition, getRate(definition, context));
      }
    }

    final ResolveResultList results = new ResolveResultList();
    for (RatedResolveResult r : ret) {
      final PsiElement e = r.getElement();
      if (e == element ||
          element instanceof PyTargetExpression && e != null && PyUtil.inSameFile(element, e) && PyPsiUtils.isBefore(element, e)) {
        continue;
      }
      results.add(changePropertyMethodToSameNameGetter(r, name));
    }
    return results;
  }

  private static boolean isInnerComprehension(PsiElement referenceElement, PsiElement definition) {
    if (definition != null && definition.getParent() instanceof PyAssignmentExpression) return false;

    final PyComprehensionElement definitionComprehension = PsiTreeUtil.getParentOfType(definition, PyComprehensionElement.class);
    if (definitionComprehension != null && PyUtil.isOwnScopeComprehension(definitionComprehension)) {
      final PyComprehensionElement elementComprehension = PsiTreeUtil.getParentOfType(referenceElement, PyComprehensionElement.class);
      if (elementComprehension == null || !PsiTreeUtil.isAncestor(definitionComprehension, elementComprehension, false)) {
        return true;
      }
    }
    return false;
  }

  private static @NotNull RatedResolveResult changePropertyMethodToSameNameGetter(@NotNull RatedResolveResult resolveResult, @NotNull String name) {
    final PsiElement element = resolveResult.getElement();
    if (element instanceof PyFunction) {
      final Property property = ((PyFunction)element).getProperty();
      if (property != null) {
        final PyCallable getter = property.getGetter().valueOrNull();
        final PyCallable setter = property.getSetter().valueOrNull();
        final PyCallable deleter = property.getDeleter().valueOrNull();

        if (getter != null && name.equals(getter.getName()) &&
            (setter == null || name.equals(setter.getName())) &&
            (deleter == null || name.equals(deleter.getName()))) {
          return resolveResult.replace(getter);
        }
      }
    }

    return resolveResult;
  }

  private boolean isInOwnScopeComprehension(@Nullable PsiElement uexpr) {
    if (uexpr == null || !myContext.getTypeEvalContext().maySwitchToAST(uexpr)) {
      return false;
    }
    final PyComprehensionElement comprehensionElement = PsiTreeUtil.getParentOfType(uexpr, PyComprehensionElement.class);
    return comprehensionElement != null && PyUtil.isOwnScopeComprehension(comprehensionElement);
  }

  /**
   * Does actual resolution of resolve().
   *
   * @return resolution result.
   * @see #resolve()
   */
  protected @NotNull List<RatedResolveResult> resolveInner() {
    final ResolveResultList overriddenResult = resolveByOverridingReferenceResolveProviders();
    if (!overriddenResult.isEmpty()) {
      return overriddenResult;
    }

    final String referencedName = myElement.getReferencedName();
    if (referencedName == null) return Collections.emptyList();

    if (myElement instanceof PyTargetExpression && PsiTreeUtil.getParentOfType(myElement, PyComprehensionElement.class) != null) {
      return ResolveResultList.to(myElement);
    }

    // Use real context here to enable correct completion and resolve in case of PyExpressionCodeFragment
    final PsiElement realContext = PyPsiUtils.getRealContext(myElement);

    if (!myContext.getTypeEvalContext().maySwitchToAST(realContext) && realContext instanceof PyFile) {
      return ((PyFile)realContext).multiResolveName(referencedName);
    }

    // here we have an unqualified expr. it may be defined:
    // ...in current file
    final PyResolveProcessor processor = new PyResolveProcessor(referencedName);

    final PsiElement roof = findResolveRoof(referencedName, realContext);
    PyResolveUtil.scopeCrawlUp(processor, myElement, referencedName, roof);

    return getResultsFromProcessor(referencedName, processor, realContext, roof);
  }

  protected final List<RatedResolveResult> getResultsFromProcessor(@NotNull String referencedName,
                                                                   @NotNull PyResolveProcessor processor,
                                                                   @Nullable PsiElement referenceAnchor,
                                                                   @Nullable PsiElement resolveRoof) {
    boolean unreachableLocalDeclaration = false;
    Supplier<ScopeOwner> resolveInParentScope = null;
    final ResolveResultList resultList = new ResolveResultList();
    final ScopeOwner referenceOwner = ScopeUtil.getScopeOwner(referenceAnchor);
    final TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
    final ScopeOwner resolvedOwner = processor.getOwner();

    final Collection<PsiElement> resolvedElements = processor.getElements();
    if (resolvedOwner != null && !resolvedElements.isEmpty() && !ControlFlowCache.getScope(resolvedOwner).isGlobal(referencedName)) {
      if (resolvedOwner == referenceOwner && referenceAnchor != null) {
        final List<Instruction> instructions = getLatestDefinitions(referencedName, resolvedOwner, referenceAnchor);
        // TODO: Use the results from the processor as a cache for resolving to latest defs
        final ResolveResultList latestDefs = resolveToLatestDefs(instructions, referenceAnchor, referencedName, typeEvalContext);
        if (!latestDefs.isEmpty()) {
          return StreamEx.of(latestDefs)
            .flatMap(r -> {
              if (r.getClass() != RatedResolveResult.class || !(r.getElement() instanceof PyFunction pyFunction)) {
                return StreamEx.of(r);
              }
              List<PyFunction> overloads = PyiUtil.getOverloads(pyFunction, typeEvalContext);
              if (overloads.isEmpty()) {
                return StreamEx.of(r);
              }
              return StreamEx.of(overloads)
                .map(overload -> new RatedResolveResult(getRate(overload, typeEvalContext), overload))
                .prepend(StreamEx.ofNullable(PyiUtil.isOverload(pyFunction, typeEvalContext) ? null : r));
            })
            .toImmutableList();
        }
        else if (resolvedOwner instanceof PyClass && !(referenceAnchor instanceof PyTargetExpression)) {
          resolveInParentScope = () -> PyResolveUtil.parentScopeForUnresolvedClassLevelName((PyClass)resolvedOwner, referencedName);
        }
        else if (instructions.isEmpty() && allInOwnScopeComprehensions(resolvedElements)) {
          resolveInParentScope = () -> ScopeUtil.getScopeOwner(resolvedOwner);
        }
        else {
          unreachableLocalDeclaration = true;
        }
      }
      else if (referenceOwner != null) {
        if (!allowsForwardOutgoingReferencesInClass(myElement)) {
          final PyClass outermostNestedClass = outermostNestedClass(referenceOwner, resolvedOwner);

          if (outermostNestedClass != null) {
            final List<Instruction> instructions =
              PyDefUseUtil.getLatestDefs(resolvedOwner, referencedName, outermostNestedClass, false, true, typeEvalContext);

            return resolveToLatestDefs(instructions, outermostNestedClass, referencedName, typeEvalContext);
          }
        }

        final Scope referenceScope = ControlFlowCache.getScope(referenceOwner);
        if (referenceScope.containsDeclaration(referencedName)) {
          unreachableLocalDeclaration = true;
        }
      }
    }

    // TODO: Try resolve to latest defs for outer scopes starting from the last element in CFG (=> no need for a special rate for globals)

    if (!unreachableLocalDeclaration) {
      if (resolveInParentScope != null) {
        processor = new PyResolveProcessor(referencedName);
        final ScopeOwner parentScope = resolveInParentScope.get();
        if (parentScope != null) {
          PyResolveUtil.scopeCrawlUp(processor, parentScope, referencedName, resolveRoof);
        }
      }

      for (Map.Entry<PsiElement, PyImportedNameDefiner> entry : processor.getResults().entrySet()) {
        final PsiElement resolved = entry.getKey();
        final PyImportedNameDefiner definer = entry.getValue();
        if (resolved != null) {
          if (typeEvalContext.maySwitchToAST(resolved) && isInnerComprehension(referenceAnchor, resolved)) {
            continue;
          }
          if (definer == null) {
            resultList.poke(resolved, getRate(resolved, typeEvalContext));
          }
          else {
            resultList.poke(definer, getRate(definer, typeEvalContext));
            resultList.add(new ImportedResolveResult(resolved, getRate(resolved, typeEvalContext), definer));
          }
        }
        else if (definer != null) {
          resultList.add(new ImportedResolveResult(null, RatedResolveResult.RATE_LOW, definer));
        }
      }

      if (!resultList.isEmpty()) {
        return resultList;
      }
    }

    return resolveByReferenceResolveProviders();
  }

  protected @NotNull List<Instruction> getLatestDefinitions(@NotNull String referencedName,
                                                            @NotNull ScopeOwner resolvedOwner,
                                                            @NotNull PsiElement referenceAnchor) {
    return PyDefUseUtil.getLatestDefs(resolvedOwner, referencedName, referenceAnchor, false, true, myContext.getTypeEvalContext());
  }

  private boolean allInOwnScopeComprehensions(@NotNull Collection<PsiElement> elements) {
    return StreamEx.of(elements).allMatch(this::isInOwnScopeComprehension);
  }

  private static boolean allowsForwardOutgoingReferencesInClass(@NotNull PyQualifiedExpression element) {
    return ContainerUtil.exists(PyReferenceResolveProvider.EP_NAME.getExtensionList(),
                                provider -> provider.allowsForwardOutgoingReferencesInClass(element));
  }

  private static @Nullable PyClass outermostNestedClass(@NotNull ScopeOwner referenceOwner, @NotNull ScopeOwner resolvedOwner) {
    PyClass current  = PyUtil.as(referenceOwner, PyClass.class);
    ScopeOwner outer = ScopeUtil.getScopeOwner(current);

    while (outer != resolvedOwner) {
      current = PyUtil.as(outer, PyClass.class);
      if (current == null) return null;
      outer = ScopeUtil.getScopeOwner(outer);
    }

    return current;
  }

  private @NotNull ResolveResultList resolveByOverridingReferenceResolveProviders() {
    final ResolveResultList results = new ResolveResultList();
    final TypeEvalContext context = myContext.getTypeEvalContext();

    PyReferenceResolveProvider.EP_NAME.getExtensionList().stream()
      .filter(PyOverridingReferenceResolveProvider.class::isInstance)
      .map(provider -> provider.resolveName(myElement, context))
      .forEach(results::addAll);

    return results;
  }

  private @NotNull ResolveResultList resolveByReferenceResolveProviders() {
    final ResolveResultList results = new ResolveResultList();
    final TypeEvalContext context = myContext.getTypeEvalContext();
    for (PyReferenceResolveProvider provider : PyReferenceResolveProvider.EP_NAME.getExtensionList()) {
      if (provider instanceof PyOverridingReferenceResolveProvider) {
        continue;
      }
      results.addAll(provider.resolveName(myElement, context));
    }
    return results;
  }

  private PsiElement findResolveRoof(String referencedName, PsiElement realContext) {
    if (PyUtil.isClassPrivateName(referencedName)) {
      // a class-private name; limited by either class or this file
      PsiElement one = myElement;
      do {
        one = ScopeUtil.getScopeOwner(one);
      }
      while (one instanceof PyFunction);
      if (one instanceof PyClass && !PsiTreeUtil.isAncestor(((PyClass)one).getSuperClassExpressionList(), myElement, false)) {
        return one;
      }
    }

    if (myElement instanceof PyTargetExpression) {
      final ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(myElement, ScopeOwner.class);
      final Scope scope;
      if (scopeOwner != null) {
        scope = ControlFlowCache.getScope(scopeOwner);
        final String name = myElement.getName();
        if (scope.isNonlocal(name)) {
          final ScopeOwner nonlocalOwner = ScopeUtil.getDeclarationScopeOwner(myElement, referencedName);
          if (nonlocalOwner != null && !(nonlocalOwner instanceof PyFile)) {
            return nonlocalOwner;
          }
        }
        if (!scope.isGlobal(name)) {
          return scopeOwner;
        }
      }
    }
    return realContext.getContainingFile();
  }

  public static int getRate(@Nullable PsiElement elt, @NotNull TypeEvalContext context) {
    final int rate;
    if (elt instanceof PyTargetExpression && context.maySwitchToAST(elt)) {
      final PsiElement parent = elt.getParent();
      if (parent instanceof PyGlobalStatement || parent instanceof PyNonlocalStatement) {
        rate = RatedResolveResult.RATE_LOW;
      }
      else {
        rate = RatedResolveResult.RATE_NORMAL;
      }
    }
    else if (elt instanceof PyImportedNameDefiner ||
             elt instanceof PyReferenceExpression) {
      rate = RatedResolveResult.RATE_LOW;
    }
    else if (elt != null && !PyiUtil.isInsideStub(elt) && PyiUtil.isOverload(elt, context)) {
      rate = RatedResolveResult.RATE_PY_FILE_OVERLOAD;
    }
    else {
      rate = RatedResolveResult.RATE_NORMAL;
    }
    return rate;
  }

  @Override
  public @NotNull String getCanonicalText() {
    return getRangeInElement().substring(getElement().getText());
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    final ASTNode nameElement = myElement.getNameElement();
    for (PsiElement resolved : PyUtil.multiResolveTopPriority(myElement, myContext)) {
      if (resolved instanceof PyFile && newElementName.endsWith(PyNames.DOT_PY)) {
        newElementName = StringUtil.trimEnd(newElementName, PyNames.DOT_PY);
      }
      else if (resolved instanceof PyiFile && newElementName.endsWith(PyNames.DOT_PYI)) {
        newElementName = StringUtil.trimEnd(newElementName, PyNames.DOT_PYI);
      }
    }
    if (nameElement != null && PyNames.isIdentifier(newElementName)) {
      final ASTNode newNameElement = PyUtil.createNewName(myElement, newElementName);
      myElement.getNode().replaceChild(nameElement, newNameElement);
    }
    return myElement;
  }

  @Override
  public @Nullable PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

   @Override
   public boolean isReferenceTo(@NotNull PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      // may be import via alias, so don't check if names match, do simple resolve check instead
      PsiElement resolveResult = resolve();
      if (resolveResult instanceof PyImportedModule) {
        resolveResult = resolveResult.getNavigationElement();
      }
      if (element instanceof PsiDirectory) {
        if (resolveResult instanceof PyFile file) {
          if (PyUtil.isPackage(file) && file.getContainingDirectory() == element) {
            return true;
          }
        }
        else if (resolveResult instanceof PsiDirectory directory) {
          if (PyUtil.isPackage(directory, null) && directory == element) {
            return true;
          }
        }
      }
      return resolveResult == element;
    }
    if (element instanceof PsiNamedElement) {
      final String elementName = ((PsiNamedElement)element).getName();
      if ((Objects.equals(myElement.getReferencedName(), elementName) || PyNames.INIT.equals(elementName))) {
        if (!haveQualifiers(element)) {
          final ScopeOwner ourScopeOwner = ScopeUtil.getScopeOwner(getElement());
          final ScopeOwner theirScopeOwner = ScopeUtil.getScopeOwner(element);
          if (element instanceof PyParameter || element instanceof PyTargetExpression) {
            // Check if the reference is in the same or inner scope of the element scope, not shadowed by an intermediate declaration
            if (resolvesToSameLocal(element, elementName, ourScopeOwner, theirScopeOwner)) {
              return true;
            }
          }

          final List<PsiElement> resolveResults = StreamEx.of(multiResolve(false))
            .filter(r -> !(r instanceof ImplicitResolveResult))
            .map(r -> r.getElement())
            .toList();

          for (PsiElement resolveResult : resolveResults) {
            if (resolveResult == element) {
              return true;
            }

            if (!haveQualifiers(element) && ourScopeOwner != null && theirScopeOwner != null) {
              if (resolvesToSameGlobal(element, elementName, ourScopeOwner, theirScopeOwner, resolveResult)) return true;
            }

            if (resolvesToWrapper(element, resolveResult)) {
              return true;
            }
          }
        }
        if (element instanceof PyExpression expr) {
          if (PyUtil.isClassAttribute(myElement) && (PyUtil.isClassAttribute(expr) || PyUtil.isInstanceAttribute(expr))) {
            final PyClass c1 = PsiTreeUtil.getParentOfType(element, PyClass.class);
            final PyClass c2 = PsiTreeUtil.getParentOfType(myElement, PyClass.class);
            final TypeEvalContext context = myContext.getTypeEvalContext();
            if (c1 != null && c2 != null && (c1.isSubclass(c2, context) || c2.isSubclass(c1, context))) {
              return true;
            }
          }
        }
      }
    }

    return PyReferenceCustomTargetChecker.Companion.isReferenceTo(this, element);
  }

  private boolean resolvesToSameLocal(PsiElement element, String elementName, ScopeOwner ourScopeOwner, ScopeOwner theirScopeOwner) {
    final PsiElement ourContainer = findContainer(getElement());
    final PsiElement theirContainer = findContainer(element);
    if (ourContainer != null) {
      if (ourContainer == theirContainer && ourScopeOwner == theirScopeOwner) {
        return true;
      }
      if (PsiTreeUtil.isAncestor(theirContainer, ourContainer, true)) {
        if (ourContainer instanceof PyComprehensionElement && containsDeclaration((PyComprehensionElement)ourContainer, elementName)) {
            return false;
        }

        ScopeOwner owner = ourScopeOwner;
        while (owner != theirScopeOwner && owner != null) {
          if (ControlFlowCache.getScope(owner).containsDeclaration(elementName)) {
            return false;
          }
          owner = ScopeUtil.getScopeOwner(owner);
        }

        return true;
      }
    }
    return false;
  }

  private static @Nullable PsiElement findContainer(@NotNull PsiElement element) {
    final PyElement parent = PsiTreeUtil.getParentOfType(element, ScopeOwner.class, PyComprehensionElement.class);
    if (parent instanceof PyListCompExpression && LanguageLevel.forElement(element).isPython2()) {
      return findContainer(parent);
    }
    return parent;
  }

  private static boolean containsDeclaration(@NotNull PyComprehensionElement comprehensionElement, @NotNull String variableName) {
    for (PyComprehensionForComponent forComponent : comprehensionElement.getForComponents()) {
      final PyExpression iteratorVariable = forComponent.getIteratorVariable();

      if (iteratorVariable instanceof PyTupleExpression) {
        for (PyExpression variable : (PyTupleExpression)iteratorVariable) {
          if (variable instanceof PyTargetExpression && variableName.equals(variable.getName())) {
            return true;
          }
        }
      }
      else if (iteratorVariable instanceof PyTargetExpression && variableName.equals(iteratorVariable.getName())) {
        return true;
      }
    }

    return false;
  }

  private boolean resolvesToSameGlobal(@NotNull PsiElement element,
                                       @Nullable String elementName,
                                       @NotNull ScopeOwner ourScopeOwner,
                                       @NotNull ScopeOwner theirScopeOwner,
                                       @Nullable PsiElement resolveResult) {
    // Handle situations when there is no top-level declaration for globals and transitive resolve doesn't help
    final PsiFile ourFile = getElement().getContainingFile();
    final PsiFile theirFile = element.getContainingFile();
    if (ourFile == theirFile) {
      final boolean ourIsGlobal = ControlFlowCache.getScope(ourScopeOwner).isGlobal(elementName);
      final boolean theirIsGlobal = ControlFlowCache.getScope(theirScopeOwner).isGlobal(elementName);
      if (ourIsGlobal && theirIsGlobal) {
        return true;
      }
    }

    final var resolvedScopeOwner = ScopeUtil.getScopeOwner(resolveResult);
    if (resolvedScopeOwner != null && resolveResult.getContainingFile() == theirFile) {
      if (ControlFlowCache.getScope(resolvedScopeOwner).isGlobal(elementName) &&
          ControlFlowCache.getScope(theirScopeOwner).isGlobal(elementName)) {
        return true;
      }
    }

    if (resolvedScopeOwner == ourFile && ControlFlowCache.getScope(theirScopeOwner).isGlobal(elementName)) {
      return true;
    }
    return false;
  }

  protected boolean resolvesToWrapper(PsiElement element, PsiElement resolveResult) {
    if (element instanceof PyFunction && ((PyFunction) element).getContainingClass() != null && resolveResult instanceof PyTargetExpression) {
      final PyExpression assignedValue = ((PyTargetExpression)resolveResult).findAssignedValue();
      if (assignedValue instanceof PyCallExpression call) {
        final Pair<String,PyFunction> functionPair = PyCallExpressionHelper.interpretAsModifierWrappingCall(call);
        if (functionPair != null && functionPair.second == element) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean haveQualifiers(PsiElement element) {
    if (myElement.isQualified()) {
      return true;
    }
    if (element instanceof PyQualifiedExpression && ((PyQualifiedExpression)element).isQualified()) {
      return true;
    }
    return false;
  }

  @Override
  public Object @NotNull [] getVariants() {
    final List<LookupElement> ret = new ArrayList<>();

    // Use real context here to enable correct completion and resolve in case of PyExpressionCodeFragment!!!
    final PyQualifiedExpression element = CompletionUtilCoreImpl.getOriginalOrSelf(myElement);

    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(element);
    final LanguageLevel languageLevel = LanguageLevel.forElement(myElement);
    final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(element, e -> {
      if (builtinCache.isBuiltin(e)) {
        if (e instanceof PyImportElement) {
          return false;
        }

        final String name = e instanceof PyElement pyElement ? pyElement.getName() : null;
        if (PyUtil.getInitialUnderscores(name) == 1) {
          return false;
        }

        if (languageLevel.isPython2() && PyNames.PRINT.equals(name) &&
            myElement.getContainingFile() instanceof PyFile pyFile && !pyFile.hasImportFromFuture(FutureFeature.PRINT_FUNCTION)) {
          return false;
        }
      }
      else if (ScopeUtil.getScopeOwner(e) == ScopeUtil.getScopeOwner(element)) {
        return PyDefUseUtil.isDefinedBefore(e, element);
      }
      return true;
    }, null);

    PyResolveUtil.scopeCrawlUp(processor, element, null, null);

    // This method is probably called for completion, so use appropriate context here
    // in a call, include function's arg names
    final TypeEvalContext context = TypeEvalContext.codeCompletion(element.getProject(), element.getContainingFile());
    KeywordArgumentCompletionUtil.collectFunctionArgNames(element, ret, context, true);
    // include builtin names
    final PyFile builtinsFile = builtinCache.getBuiltinsFile();
    if (builtinsFile != null) {
      PyResolveUtil.scopeCrawlUp(processor, builtinsFile, null, null);
    }

    if (PyUtil.getInitialUnderscores(element.getName()) >= 2) {
      // if we're a normal module, add module's attrs
      if (PyPsiUtils.getRealContext(element).getContainingFile() instanceof PyFile) {
        for (String name : PyModuleType.getPossibleInstanceMembers()) {
          ret.add(LookupElementBuilder.create(name).withIcon(IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Field)));
        }
      }

      // if we're inside method, add implicit __class__
      if (!languageLevel.isPython2()) {
        Optional
          .ofNullable(PsiTreeUtil.getParentOfType(myElement, PyFunction.class))
          .map(PyFunction::getContainingClass)
          .ifPresent(pyClass -> ret.add(LookupElementBuilder.create(PyNames.__CLASS__)));
      }
    }

    ret.addAll(getOriginalElements(processor));
    return ret.toArray();
  }

  /**
   * Throws away fake elements used for completion internally.
   */
  protected List<LookupElement> getOriginalElements(@NotNull CompletionVariantsProcessor processor) {
    final List<LookupElement> ret = new ArrayList<>();
    for (LookupElement item : processor.getResultList()) {
      final PsiElement e = item.getPsiElement();
      if (e != null) {
        final PsiElement original = CompletionUtilCoreImpl.getOriginalElement(e);
        if (original == null) {
          continue;
        }
      }
      ret.add(item);
    }
    return ret;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
    if (isBuiltInConstant()) return null;
    final PyExpression qualifier = myElement.getQualifier();
    if (qualifier == null) {
      return HighlightSeverity.ERROR;
    }
    if (context.getType(qualifier) != null) {
      return HighlightSeverity.WARNING;
    }
    return null;
  }

  private boolean isBuiltInConstant() {
    // TODO: generalize
    String name = myElement.getReferencedName();
    return PyNames.NONE.equals(name) || "True".equals(name) || "False".equals(name) || PyNames.DEBUG.equals(name);
  }

  @Override
  public @Nullable String getUnresolvedDescription() {
    return null;
  }


  // our very own caching resolver

  private static class CachingResolver implements ResolveCache.PolyVariantResolver<PyReferenceImpl> {
    public static final CachingResolver INSTANCE = new CachingResolver();

    @Override
    public ResolveResult @NotNull [] resolve(final @NotNull PyReferenceImpl ref, final boolean incompleteCode) {
      return ref.multiResolveInner();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyReferenceImpl that = (PyReferenceImpl)o;

    if (!myElement.equals(that.myElement)) return false;
    if (!myContext.equals(that.myContext)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myElement.hashCode();
  }
}
