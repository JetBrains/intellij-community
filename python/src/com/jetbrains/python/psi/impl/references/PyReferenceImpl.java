/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl.references;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.PyDefUseUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yole
 */
public class PyReferenceImpl implements PsiReferenceEx, PsiPolyVariantReference {
  protected final PyQualifiedExpression myElement;
  protected final PyResolveContext myContext;

  public PyReferenceImpl(PyQualifiedExpression element, @NotNull PyResolveContext context) {
    myElement = element;
    myContext = context;
  }

  @Override
  public TextRange getRangeInElement() {
    final ASTNode nameElement = myElement.getNameElement();
    final TextRange range = nameElement != null ? nameElement.getTextRange() : myElement.getNode().getTextRange();
    return range.shiftRight(-myElement.getNode().getStartOffset());
  }

  @Override
  public PsiElement getElement() {
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
  @Nullable
  public PsiElement resolve() {
    final ResolveResult[] results = multiResolve(false);
    return results.length >= 1 && !(results[0] instanceof ImplicitResolveResult) ? results[0].getElement() : null;
  }

  // it is *not* final so that it can be changed in debug time. if set to false, caching is off
  @SuppressWarnings("FieldCanBeLocal")
  private static boolean USE_CACHE = true;

  /**
   * Resolves reference to possible referred elements.
   * First element is always what resolve() would return.
   * Imported module names: to module file, or {directory, '__init__.py}' for a qualifier.
   * todo Local identifiers: a list of definitions in the most recent compound statement
   * (e.g. <code>if X: a = 1; else: a = 2</code> has two definitions of <code>a</code>.).
   * todo Identifiers not found locally: similar definitions in imported files and builtins.
   *
   * @see com.intellij.psi.PsiPolyVariantReference#multiResolve(boolean)
   */
  @Override
  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    if (USE_CACHE) {
      final ResolveCache cache = ResolveCache.getInstance(getElement().getProject());
      return cache.resolveWithCaching(this, CachingResolver.INSTANCE, false, incompleteCode);
    }
    else {
      return multiResolveInner();
    }
  }

  // sorts and modifies results of resolveInner

  @NotNull
  private ResolveResult[] multiResolveInner() {
    final String referencedName = myElement.getReferencedName();
    if (referencedName == null) return ResolveResult.EMPTY_ARRAY;

    List<RatedResolveResult> targets = resolveInner();
    if (targets.size() == 0) return ResolveResult.EMPTY_ARRAY;

    // change class results to constructor results if there are any
    if (myElement.getParent() instanceof PyCallExpression) { // we're a call
      ListIterator<RatedResolveResult> it = targets.listIterator();
      while (it.hasNext()) {
        final RatedResolveResult rrr = it.next();
        final PsiElement elt = rrr.getElement();
        if (elt instanceof PyClass) {
          PyClass cls = (PyClass)elt;
          PyFunction init = cls.findMethodByName(PyNames.INIT, false);
          if (init != null) {
            // replace
            it.set(rrr.replace(init));
          }
          else { // init not found; maybe it's ancestor's
            for (PyClass ancestor : cls.getAncestorClasses(myContext.getTypeEvalContext())) {
              init = ancestor.findMethodByName(PyNames.INIT, false);
              if (init != null) {
                // add to results as low priority
                it.add(new RatedResolveResult(RatedResolveResult.RATE_LOW, init));
                break;
              }
            }
          }
        }
      }
    }

    // put everything in a sorting container
    List<RatedResolveResult> ret = RatedResolveResult.sorted(targets);
    return ret.toArray(new ResolveResult[ret.size()]);
  }

  @NotNull
  private static ResolveResultList resolveToLatestDefs(@NotNull List<ReadWriteInstruction> instructions, @NotNull PsiElement element, @NotNull String name) {
    final ResolveResultList ret = new ResolveResultList();
    for (ReadWriteInstruction instruction : instructions) {
      PsiElement definition = instruction.getElement();
      NameDefiner definer = null;
      // TODO: This check may slow down resolving, but it is the current solution to the comprehension scopes problem
      if (isInnerComprehension(element, definition)) continue;
      if (definition instanceof NameDefiner && !(definition instanceof PsiNamedElement)) {
        definer = (NameDefiner)definition;
        definition = definer.getElementNamed(name);
      }
      if (definer != null) {
        if (definer instanceof PyImportElement || definer instanceof PyStarImportElement || definer instanceof PyImportedModule) {
          ret.add(new ImportedResolveResult(definition, getRate(definition), Collections.<PsiElement>singletonList(definer)));
        }
        else {
          ret.poke(definition, getRate(definition));
        }
        // TODO this kind of resolve contract is quite stupid
        if (definition != null) {
          ret.poke(definer, RatedResolveResult.RATE_LOW);
        }
      }
      else {
        ret.poke(definition, getRate(definition));
      }
    }
    final ResolveResultList results = new ResolveResultList();
    for (RatedResolveResult r : ret) {
      final PsiElement e = r.getElement();
      if (e == element) {
        continue;
      }
      if (element instanceof PyTargetExpression && PyPsiUtils.isBefore(element, e)) {
        continue;
      }
      else {
        results.add(r);
      }
    }

    return results;
  }

  private static boolean isInnerComprehension(PsiElement referenceElement, PsiElement definition) {
    final PyComprehensionElement definitionComprehension = PsiTreeUtil.getParentOfType(definition, PyComprehensionElement.class);
    if (definitionComprehension != null && PyUtil.isOwnScopeComprehension(definitionComprehension)) {
      final PyComprehensionElement elementComprehension = PsiTreeUtil.getParentOfType(referenceElement, PyComprehensionElement.class);
      if (elementComprehension == null || !PsiTreeUtil.isAncestor(definitionComprehension, elementComprehension, false)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isInOwnScopeComprehension(PsiElement uexpr) {
    PyComprehensionElement comprehensionElement = PsiTreeUtil.getParentOfType(uexpr, PyComprehensionElement.class);
    return comprehensionElement != null && PyUtil.isOwnScopeComprehension(comprehensionElement);
  }

  /**
   * Does actual resolution of resolve().
   *
   * @return resolution result.
   * @see #resolve()
   */
  @NotNull
  protected List<RatedResolveResult> resolveInner() {
    final ResolveResultList ret = new ResolveResultList();

    final String referencedName = myElement.getReferencedName();
    if (referencedName == null) return ret;

    if (myElement instanceof PyTargetExpression) {
      if (PsiTreeUtil.getParentOfType(myElement, PyComprehensionElement.class) != null) {
        ret.poke(myElement, getRate(myElement));
        return ret;
      }
    }

    // here we have an unqualified expr. it may be defined:
    // ...in current file
    ResolveProcessor processor = new ResolveProcessor(referencedName);

    // Use real context here to enable correct completion and resolve in case of PyExpressionCodeFragment
    final PsiElement realContext = PyPsiUtils.getRealContext(myElement);

    PsiElement roof = findResolveRoof(referencedName, realContext);
    PyResolveUtil.scopeCrawlUp(processor, myElement, referencedName, roof);
    return getResultsFromProcessor(referencedName, processor, realContext, roof);
  }

  protected List<RatedResolveResult> getResultsFromProcessor(String referencedName,
                                                             ResolveProcessor processor,
                                                             PsiElement realContext, PsiElement roof) {
    ResolveResultList ret = new ResolveResultList();
    PsiElement uexpr = processor.getResult();
    List<PsiElement> definers = processor.getDefiners();
    if (uexpr != null) {
      if (definers.isEmpty()) {
        final ScopeOwner originalOwner = ScopeUtil.getScopeOwner(realContext);
        final ScopeOwner owner = ScopeUtil.getScopeOwner(uexpr);
        if (owner != null) {
          final Scope scope = ControlFlowCache.getScope(owner);
          if (uexpr == originalOwner && originalOwner instanceof PyClass) {
            uexpr = null;
          }
          else if (owner == originalOwner && !scope.isGlobal(referencedName)) {
            final List<ReadWriteInstruction> instructions = PyDefUseUtil.getLatestDefs(owner, referencedName, myElement, false);
            final ResolveResultList latest = resolveToLatestDefs(instructions, myElement, referencedName);
            if (!latest.isEmpty()) {
              return latest;
            }
            if (owner instanceof PyClass || (instructions.isEmpty() && isInOwnScopeComprehension(uexpr))) {
              final ScopeOwner parentOwner = ScopeUtil.getScopeOwner(owner);
              if (parentOwner != null) {
                processor = new ResolveProcessor(referencedName);
                PyResolveUtil.scopeCrawlUp(processor, parentOwner, referencedName, roof);
                uexpr = processor.getResult();
                definers = processor.getDefiners();
              }
            }
            else {
              uexpr = null;
            }
          }
          else if (owner != originalOwner && originalOwner != null && !scope.isGlobal(referencedName)) {
            final Scope originalScope = ControlFlowCache.getScope(originalOwner);
            if (originalScope.containsDeclaration(referencedName)) {
              uexpr = null;
            }
          }
        }
      }
      // sort what we got
      for (PsiElement hit : definers) {
        ret.poke(hit, getRate(hit));
      }
      final PsiElement packageInit = PyUtil.turnDirIntoInit(uexpr);
      if (packageInit != null) {
        uexpr = packageInit; // an import statement may have returned a dir
      }
    }
    else if (!definers.isEmpty()) {
      ret.add(new ImportedResolveResult(null, RatedResolveResult.RATE_LOW, definers));
    }
    PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(realContext);
    if (uexpr == null) {
      // ...as a part of current module
      String name = myElement.getName();
      if (PyModuleType.MODULE_MEMBERS.contains(name)) {
        PyType objectType = builtinCache.getObjectType(); // "object" as a closest kin to "module"
        if (objectType != null && name != null) {
          ret.addAll(objectType.resolveMember(name, null, AccessDirection.READ, myContext));
        }
      }
    }
    if (uexpr != null) {
      ret.add(new ImportedResolveResult(uexpr, getRate(uexpr), definers));
    }
    else {
      for (PyReferenceResolveProvider provider : Extensions.getExtensions(PyReferenceResolveProvider.EP_NAME)) {
        final List<RatedResolveResult> results = provider.resolveName(myElement, definers);
        for (RatedResolveResult res : results) {
          ret.add(res);
        }
      }
    }

    return ret;
  }

  private PsiElement findResolveRoof(String referencedName, PsiElement realContext) {
    if (PyUtil.isClassPrivateName(referencedName)) {
      // a class-private name; limited by either class or this file
      PsiElement one = myElement;
      do {
        one = PyUtil.getConcealingParent(one);
      }
      while (one instanceof PyFunction);
      if (one instanceof PyClass) {
        PyArgumentList superClassExpressionList = ((PyClass)one).getSuperClassExpressionList();
        if (superClassExpressionList == null || !PsiTreeUtil.isAncestor(superClassExpressionList, myElement, false)) {
          return one;
        }
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

  // NOTE: very crude

  public static int getRate(PsiElement elt) {
    int rate;
    if (elt instanceof PyImportedNameDefiner || elt instanceof PyReferenceExpression) {
      rate = RatedResolveResult.RATE_LOW;
    }
    else if (elt instanceof PyFile) {
      rate = RatedResolveResult.RATE_HIGH;
    }
    else {
      rate = RatedResolveResult.RATE_NORMAL;
    }
    return rate;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return getRangeInElement().substring(getElement().getText());
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    ASTNode nameElement = myElement.getNameElement();
    if (newElementName.endsWith(PyNames.DOT_PY)) {
      newElementName = newElementName.substring(0, newElementName.length() - PyNames.DOT_PY.length());
    }
    if (nameElement != null && PyNames.isIdentifier(newElementName)) {
      final ASTNode newNameElement = PyUtil.createNewName(myElement, newElementName);
      myElement.getNode().replaceChild(nameElement, newNameElement);
    }
    return myElement;
  }

  @Override
  @Nullable
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

   @Override
   public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      // may be import via alias, so don't check if names match, do simple resolve check instead
      PsiElement resolveResult = resolve();
      if (resolveResult instanceof PyImportedModule) {
        resolveResult = resolveResult.getNavigationElement();
      }
      if (element instanceof PsiDirectory) {
        if (resolveResult instanceof PyFile) {
          final PyFile file = (PyFile)resolveResult;
          if (PyUtil.isPackage(file) && file.getContainingDirectory() == element) {
            return true;
          }
        }
        else if (resolveResult instanceof PsiDirectory) {
          final PsiDirectory directory = (PsiDirectory)resolveResult;
          if (PyUtil.isPackage(directory) && directory == element) {
            return true;
          }
        }
      }
      return resolveResult == element;
    }
    if (element instanceof PsiNamedElement) {
      final String elementName = ((PsiNamedElement)element).getName();
      if ((Comparing.equal(myElement.getReferencedName(), elementName) || PyNames.INIT.equals(elementName))) {
        if (!haveQualifiers(element)) {
          final ScopeOwner ourScopeOwner = ScopeUtil.getScopeOwner(getElement());
          final ScopeOwner theirScopeOwner = ScopeUtil.getScopeOwner(element);
          if (element instanceof PyParameter || element instanceof PyTargetExpression) {
            // Check if the reference is in the same or inner scope of the element scope, not shadowed by an intermediate declaration
            if (resolvesToSameLocal(element, elementName, ourScopeOwner, theirScopeOwner)) {
              return true;
            }
          }

          final PsiElement resolveResult = resolve();
          if (resolveResult == element) {
            return true;
          }

          // we shadow their name or they shadow ours (PY-6241)
          if (resolveResult instanceof PsiNamedElement && resolveResult instanceof ScopeOwner && element instanceof ScopeOwner &&
              theirScopeOwner == ScopeUtil.getScopeOwner(resolveResult)) {
            return true;
          }

          if (!haveQualifiers(element) && ourScopeOwner != null && theirScopeOwner != null) {
            if (resolvesToSameGlobal(element, elementName, ourScopeOwner, theirScopeOwner, resolveResult)) return true;
          }

          if (resolvesToWrapper(element, resolveResult)) {
            return true;
          }
        }
        if (element instanceof PyExpression) {
          final PyExpression expr = (PyExpression)element;
          if (PyUtil.isClassAttribute(myElement) && (PyUtil.isClassAttribute(expr) || PyUtil.isInstanceAttribute(expr))) {
            final PyClass c1 = PsiTreeUtil.getParentOfType(element, PyClass.class);
            final PyClass c2 = PsiTreeUtil.getParentOfType(myElement, PyClass.class);
            if (c1 != null && c2 != null && (c1.isSubclass(c2) || c2.isSubclass(c1))) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private boolean resolvesToSameLocal(PsiElement element, String elementName, ScopeOwner ourScopeOwner, ScopeOwner theirScopeOwner) {
    final PsiElement ourContainer = findContainer(getElement());
    final PsiElement theirContainer = findContainer(element);
    if (ourContainer != null) {
      if (ourContainer == theirContainer) {
        return true;
      }
      if (PsiTreeUtil.isAncestor(theirContainer, ourContainer, true)) {
        if (ourScopeOwner != theirScopeOwner) {
          boolean shadowsName = false;
          ScopeOwner owner = ourScopeOwner;
          while(owner != theirScopeOwner && owner != null) {
            if (ControlFlowCache.getScope(owner).containsDeclaration(elementName)) {
              shadowsName = true;
              break;
            }
            owner = ScopeUtil.getScopeOwner(owner);
          }
          if (!shadowsName) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Nullable
  private static PsiElement findContainer(@NotNull PsiElement element) {
    final PyElement parent = PsiTreeUtil.getParentOfType(element, ScopeOwner.class, PyComprehensionElement.class);
    if (parent instanceof PyListCompExpression && LanguageLevel.forElement(element).isOlderThan(LanguageLevel.PYTHON30)) {
      return findContainer(parent);
    }
    return parent;
  }

  private boolean resolvesToSameGlobal(PsiElement element, String elementName, ScopeOwner ourScopeOwner, ScopeOwner theirScopeOwner,
                                       PsiElement resolveResult) {
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
    if (ScopeUtil.getScopeOwner(resolveResult) == ourFile && ControlFlowCache.getScope(theirScopeOwner).isGlobal(elementName)) {
      return true;
    }
    return false;
  }

  protected boolean resolvesToWrapper(PsiElement element, PsiElement resolveResult) {
    if (element instanceof PyFunction && ((PyFunction) element).getContainingClass() != null && resolveResult instanceof PyTargetExpression) {
      final PyExpression assignedValue = ((PyTargetExpression)resolveResult).findAssignedValue();
      if (assignedValue instanceof PyCallExpression) {
        final PyCallExpression call = (PyCallExpression)assignedValue;
        final Pair<String,PyFunction> functionPair = PyCallExpressionHelper.interpretAsModifierWrappingCall(call, myElement);
        if (functionPair != null && functionPair.second == element) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean haveQualifiers(PsiElement element) {
    if (myElement.getQualifier() != null) {
      return true;
    }
    if (element instanceof PyQualifiedExpression && ((PyQualifiedExpression)element).getQualifier() != null) {
      return true;
    }
    return false;
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    final List<LookupElement> ret = Lists.newArrayList();

    // Use real context here to enable correct completion and resolve in case of PyExpressionCodeFragment!!!
    final PsiElement originalElement = CompletionUtil.getOriginalElement(myElement);
    final PyQualifiedExpression element = originalElement instanceof PyQualifiedExpression ?
                                          (PyQualifiedExpression)originalElement : myElement;
    final PsiElement realContext = PyPsiUtils.getRealContext(element);

    // include our own names
    final int underscores = PyUtil.getInitialUnderscores(element.getName());
    final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(element);
    final ScopeOwner owner = realContext instanceof ScopeOwner ? (ScopeOwner)realContext : ScopeUtil.getScopeOwner(realContext);
    if (owner != null) {
      PyResolveUtil.scopeCrawlUp(processor, owner, null, null);
    }

    // in a call, include function's arg names
    KeywordArgumentCompletionUtil.collectFunctionArgNames(element, ret);

    // include builtin names
    final PyFile builtinsFile = PyBuiltinCache.getInstance(element).getBuiltinsFile();
    if (builtinsFile != null) {
      PyResolveUtil.scopeCrawlUp(processor, builtinsFile, null, null);
    }

    if (underscores >= 2) {
      // if we're a normal module, add module's attrs
      PsiFile f = realContext.getContainingFile();
      if (f instanceof PyFile) {
        for (String name : PyModuleType.getPossibleInstanceMembers()) {
          ret.add(LookupElementBuilder.create(name).withIcon(PlatformIcons.FIELD_ICON));
        }
      }
    }

    // Throw away fake elements used for completion internally
    for (LookupElement e : processor.getResultList()) {
      final Object o = e.getObject();
      if (o instanceof PsiElement) {
        final PsiElement original = CompletionUtil.getOriginalElement((PsiElement)o);
        if (original == null) {
          continue;
        }
      }
      ret.add(e);
    }

    return ret.toArray();
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
    return PyNames.NONE.equals(name) || "True".equals(name) || "False".equals(name);
  }

  @Override
  @Nullable
  public String getUnresolvedDescription() {
    return null;
  }


  // our very own caching resolver

  private static class CachingResolver implements ResolveCache.PolyVariantResolver<PyReferenceImpl> {
    public static CachingResolver INSTANCE = new CachingResolver();
    private ThreadLocal<AtomicInteger> myNesting = new ThreadLocal<AtomicInteger>() {
      @Override
      protected AtomicInteger initialValue() {
        return new AtomicInteger();
      }
    };

    private static final int MAX_NESTING_LEVEL = 30;

    @Override
    @NotNull
    public ResolveResult[] resolve(@NotNull final PyReferenceImpl ref, final boolean incompleteCode) {
      if (myNesting.get().getAndIncrement() >= MAX_NESTING_LEVEL) {
        System.out.println("Stack overflow pending");
      }
      try {
        return ref.multiResolveInner();
      }
      finally {
        myNesting.get().getAndDecrement();
      }
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

  protected static Object[] getTypeCompletionVariants(PyExpression pyExpression, PyType type) {
    ProcessingContext context = new ProcessingContext();
    context.put(PyType.CTX_NAMES, new HashSet<String>());
    return type.getCompletionVariants(pyExpression.getName(), pyExpression, context);
  }
}
