package com.jetbrains.python.psi.impl;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.SortedList;
import com.jetbrains.django.util.PythonDataflowUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
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

  public TextRange getRangeInElement() {
    final ASTNode nameElement = myElement.getNameElement();
    final int startOffset = nameElement != null ? nameElement.getStartOffset() : myElement.getNode().getTextRange().getEndOffset();
    return new TextRange(startOffset - myElement.getNode().getStartOffset(), myElement.getTextLength());
  }

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
  @Nullable
  public PsiElement resolve() {
    final ResolveResult[] results = multiResolve(false);
    return results.length >= 1 && !(results[0] instanceof ImplicitResolveResult) ? results[0].getElement() : null;
  }

  // it is *not* final so that it can be changed in debug time. if set to false, caching is off
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
  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    final PsiManager manager = getElement().getManager();
    if (USE_CACHE && manager instanceof PsiManagerImpl) {
      final ResolveCache cache = ((PsiManagerImpl)manager).getResolveCache();
      return cache.resolveWithCaching(this, CachingResolver.INSTANCE, false, incompleteCode);
    }
    else {
      return multiResolveInner(incompleteCode);
    }
  }

  // sorts and modifies results of resolveInner

  private ResolveResult[] multiResolveInner(boolean incomplete) {
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
            for (PyClass ancestor : cls.iterateAncestorClasses()) {
              init = ancestor.findMethodByName(PyNames.INIT, false);
              if (init != null) {
                // add to resuls as low priority
                it.add(new RatedResolveResult(RatedResolveResult.RATE_LOW, init));
                break;
              }
            }
          }
        }
      }
    }

    // put everything in a sorting container
    List<RatedResolveResult> ret = new SortedList<RatedResolveResult>(new Comparator<RatedResolveResult>() {
      public int compare(final RatedResolveResult one, final RatedResolveResult another) {
        return another.getRate() - one.getRate();
      }
    });
    ret.addAll(targets);

    return ret.toArray(new ResolveResult[ret.size()]);
  }

  protected static class ResultList extends ArrayList<RatedResolveResult> {
    // Allows to add non-null elements and discard nulls in a hassle-free way.

    public boolean poke(final PsiElement what, final int rate) {
      if (what == null) return false;
      super.add(new RatedResolveResult(rate, what));
      return true;
    }
  }


  /**
   * Does actual resolution of resolve().
   *
   * @return resolution result.
   * @see #resolve()
   */
  @NotNull
  protected List<RatedResolveResult> resolveInner() {
    ResultList ret = new ResultList();

    final String referencedName = myElement.getReferencedName();
    if (referencedName == null) return ret;

    // here we have an unqualified expr. it may be defined:
    // ...in current file
    ResolveProcessor processor = new ResolveProcessor(referencedName);

    // Use real context here to enable correct completion and resolve in case of PyExpressionCodeFragment!!!
    PsiElement realContext = PyPsiUtils.getRealContext(myElement);
    PyClass containingClass = PsiTreeUtil.getParentOfType(realContext, PyClass.class);
    if (containingClass != null && PsiTreeUtil.isAncestor(containingClass.getSuperClassExpressionList(), myElement, false)) {
      realContext = containingClass;
    }

    PsiElement roof = findResolveRoof(referencedName, realContext);
    PsiElement uexpr = PyResolveUtil.treeCrawlUp(processor, false, realContext, roof);
    if ((uexpr != null)) {
      // sort what we got
      for (NameDefiner hit : processor.getDefiners()) {
        ret.poke(hit, getRate(hit));
      }
      uexpr = PyUtil.turnDirIntoInit(uexpr); // an import statement may have returned a dir
    }
    else if (!processor.getDefiners().isEmpty()) {
      ret.add(new ImportedResolveResult(null, RatedResolveResult.RATE_LOW-1, processor.getDefiners()));
    }
    PyBuiltinCache builtins_cache = PyBuiltinCache.getInstance(realContext);
    if (uexpr == null) {
      // ...as a part of current module
      PyType otype = builtins_cache.getObjectType(); // "object" as a closest kin to "module"
      if (otype != null) {
        final List<? extends PsiElement> members = otype.resolveMember(myElement.getName(), null, AccessDirection.READ, myContext);
        if (members != null) {
          int rate = RatedResolveResult.RATE_NORMAL;
          for (PsiElement member : members) {
            ret.poke(member, rate);
            rate = RatedResolveResult.RATE_LOW;
          }
        }
      }
    }
    if (uexpr == null) {
      // ...as a builtin symbol
      PyFile bfile = builtins_cache.getBuiltinsFile();
      if (bfile != null) {
        uexpr = bfile.getElementNamed(referencedName);
        if (uexpr == null && "__builtins__".equals(referencedName)) {
          uexpr = bfile; // resolve __builtins__ reference
        }
      }
    }
    if (uexpr == null && !(myElement instanceof PyTargetExpression)) {
      //uexpr = PyResolveUtil.resolveOffContext(this);
      final PsiElement outerContextElement = PyResolveUtil.scanOuterContext(new ResolveProcessor(referencedName), realContext);
      uexpr = PyUtil.turnDirIntoInit(outerContextElement);
    }
    if (uexpr != null) {
      ret.add(new ImportedResolveResult(uexpr, getRate(uexpr), processor.getDefiners()));
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
      final Scope scope = ControlFlowCache.getScope(scopeOwner);
      final String name = myElement.getName();
      if (scope.isNonlocal(name)) {
        final ScopeOwner nonlocalOwner = ScopeUtil.getDeclarationScopeOwner(myElement, referencedName);
        if (nonlocalOwner != null && !(nonlocalOwner instanceof PyFile)) {
          return nonlocalOwner;
        }
      }
      if (scopeOwner != null && !scope.isGlobal(name)) {
        return scopeOwner;
      }
    }
    return realContext.getContainingFile();
  }

  private boolean isSuperClassExpression(PyClass cls) {
    if (myElement.getContainingFile() != cls.getContainingFile()) {  // quick check to avoid unnecessary tree loading
      return false;
    }
    for (PyExpression base_expr : cls.getSuperClassExpressions()) {
      if (base_expr == this) {
        return true;
      }
    }
    return false;
  }

  // NOTE: very crude

  private static int getRate(PsiElement elt) {
    int rate;
    if (elt instanceof PyImportElement || elt instanceof PyStarImportElement) {
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

  @NotNull
  public String getCanonicalText() {
    return getRangeInElement().substring(getElement().getText());
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    ASTNode nameElement = myElement.getNameElement();
    if (nameElement != null) {
      final ASTNode newNameElement = PyElementGenerator.getInstance(myElement.getProject()).createNameIdentifier(newElementName);
      myElement.getNode().replaceChild(nameElement, newNameElement);
    }
    return myElement;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  private static PsiElement transitiveResolve(PsiElement element) {
    PsiElement prev = null;
    while (element != prev) {
      prev = element;
      PsiReference ref = element.getReference();
      if (ref != null) {
        PsiElement e = ref.resolve();
        if (e != null) {
          element = e;
        }
      }
    }
    return element;
  }

  private static boolean isGlobal(PsiElement anchor, String name) {
    final ScopeOwner owner = ScopeUtil.getDeclarationScopeOwner(anchor, name);
    if (owner != null) {
      return ControlFlowCache.getScope(owner).isGlobal(name);
    }
    return false;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      // may be import via alias, so don't check if names match, do simple resolve check instead
      final PsiElement resolveResult = resolve();
      if (element instanceof PsiDirectory && resolveResult instanceof PyFile &&
          PyNames.INIT_DOT_PY.equals(((PyFile)resolveResult).getName()) && ((PyFile)resolveResult).getContainingDirectory() == element) {
        return true;
      }
      return resolveResult == element;
    }
    if (element instanceof PsiNamedElement) {
      final String elementName = ((PsiNamedElement)element).getName();
      if ((Comparing.equal(myElement.getReferencedName(), elementName) || PyNames.INIT.equals(elementName)) && !haveQualifiers(element)) {
        // Global elements may in fact be resolved to their outer declarations
        if (isGlobal(element, elementName)) {
          element = transitiveResolve(element);
        }
        final ScopeOwner ourScopeOwner = ScopeUtil.getScopeOwner(getElement());
        final ScopeOwner theirScopeOwner = ScopeUtil.getScopeOwner(element);
        if (element instanceof PyParameter || element instanceof PyTargetExpression) {
          // Check if the reference is in the same or inner scope of the element scope, not shadowed by an intermediate declaration
          PsiElement ourContainer = PsiTreeUtil.getParentOfType(getElement(), PsiNamedElement.class, PyLambdaExpression.class, PyComprehensionElement.class);
          PsiElement theirContainer = PsiTreeUtil.getParentOfType(element, PsiNamedElement.class, PyLambdaExpression.class, PyComprehensionElement.class);
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
        }

        final PsiElement resolveResult = (isGlobal(getElement(), elementName)) ? transitiveResolve(getElement()) : resolve();
        if (resolveResult == element) {
          return true;
        }

        if (!haveQualifiers(element) && ourScopeOwner != null && theirScopeOwner != null) {
          // Handle situations when there is no top-level declaration for globals and transitive resolve doesn't help
          final boolean ourIsGlobal = ControlFlowCache.getScope(ourScopeOwner).isGlobal(elementName);
          final boolean theirIsGlobal = ControlFlowCache.getScope(theirScopeOwner).isGlobal(elementName);
          final PsiFile ourFile = getElement().getContainingFile();
          final PsiFile theirFile = element.getContainingFile();

          if (ourIsGlobal && theirIsGlobal && ourFile == theirFile) {
            return true;
          }
          if (theirIsGlobal && ScopeUtil.getScopeOwner(resolveResult) == ourFile) {
            return true;
          }
        }
        return false; // TODO: handle multi-resolve
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

  private static final TokenSet IS_STAR_IMPORT = TokenSet.create(PyElementTypes.STAR_IMPORT_ELEMENT);

  @NotNull
  public Object[] getVariants() {
    final List<LookupElement> ret = Lists.newArrayList();

    // Use real context here to enable correct completion and resolve in case of PyExpressionCodeFragment!!!
    final PsiElement realContext = PyPsiUtils.getRealContext(myElement);

    // include our own names
    final int underscores = PyUtil.getInitialUnderscores(myElement.getName());
    final VariantsProcessor processor = new VariantsProcessor(myElement);
    PyResolveUtil.treeCrawlUp(processor, realContext); // names from here
    PyResolveUtil.scanOuterContext(processor, realContext); // possible names from around us at call time

    // in a call, include function's arg names
    PythonDataflowUtil.collectFunctionArgNames(myElement, ret);

    // scan all "import *" and include names provided by them
    CollectProcessor collect_proc = new CollectProcessor(IS_STAR_IMPORT);
    PyResolveUtil.treeCrawlUp(collect_proc, realContext);
    List<PsiElement> stars = collect_proc.getResult();
    for (PsiElement star_elt : stars) {
      final PyFromImportStatement from_import_stmt = (PyFromImportStatement)star_elt.getParent();
      if (from_import_stmt != null) {
        final PyReferenceExpression import_src = from_import_stmt.getImportSource();
        if (import_src != null) {
          final String imported_name = import_src.getName();
          processor.setNotice(imported_name);
          final PsiElement importedModule = import_src.getReference().resolve();
          List<String> dunderAll = null;
          if (importedModule instanceof PyFile) {
            dunderAll = ((PyFile) importedModule).getDunderAll();
          }
          processor.setAllowedNames(dunderAll);
          try {
            PyResolveUtil.treeCrawlUp(processor, true, importedModule); // names from that module
            processor.addVariantsFromAllowedNames();
          }
          finally {
            processor.setAllowedNames(null);
          }
        }
      }
    }
    // include builtin names
    processor.setNotice("__builtin__");
    PyResolveUtil.treeCrawlUp(processor, true, PyBuiltinCache.getInstance(getElement()).getBuiltinsFile()); // names from __builtin__

    if (underscores >= 2) {
      // if we're a normal module, add module's attrs
      PsiFile f = realContext.getContainingFile();
      if (f instanceof PyFile) {
        for (String name : PyModuleType.getPossibleInstanceMembers()) {
          ret.add(LookupElementBuilder.create(name).setIcon(Icons.FIELD_ICON));
        }
      }
    }

    ret.addAll(processor.getResultList());
    return ret.toArray();
  }

  public boolean isSoft() {
    return false;
  }

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

    @Nullable
    public ResolveResult[] resolve(final PyReferenceImpl ref, final boolean incompleteCode) {
      if (myNesting.get().getAndIncrement() >= MAX_NESTING_LEVEL) {
        System.out.println("Stack overflow pending");
      }
      try {
        return ref.multiResolveInner(incompleteCode);
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
