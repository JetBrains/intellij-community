package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.SortedList;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
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
            for (PyClass ancestor : cls.iterateAncestors()) {
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
    final PsiElement realContext = PyPsiUtils.getRealContext(myElement);
    PsiElement roof = null;
    if (PyUtil.isClassPrivateName(referencedName)) {
      // a class-private name; limited by either class or this file
      PsiElement one = myElement;
      do {
        one = PyUtil.getConcealingParent(one);
      }
      while (one instanceof PyFunction);
      if (one instanceof PyClass) roof = one;
    }
    if (roof == null) roof = realContext.getContainingFile();
    PsiElement uexpr = PyResolveUtil.treeCrawlUp(processor, false, realContext, roof);
    if ((uexpr != null)) {
      if ((uexpr instanceof PyClass)) {
        // is it a case of the bizarre "class Foo(Foo)" construct?
        PyClass cls = (PyClass)uexpr;
        if (isSuperClassExpression(cls)) {
          ret.clear();
          return ret; // cannot resolve us, the base class ref, to the class being defined
        }
      }
      // sort what we got
      for (NameDefiner hit : processor.getDefiners()) {
        ret.poke(hit, getRate(hit));
      }
    }
    else if (!processor.getDefiners().isEmpty()) {
      ret.add(new ImportedResolveResult(null, RatedResolveResult.RATE_LOW-1, processor.getDefiners()));
    }
    if (uexpr == null) {
      // ...as a part of current module
      PyType otype = PyBuiltinCache.getInstance(realContext).getObjectType(); // "object" as a closest kin to "module"
      if (otype != null) {
        final List<? extends PsiElement> members = otype.resolveMember(myElement.getName(), AccessDirection.READ);
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
      PyFile bfile = PyBuiltinCache.getInstance(realContext).getBuiltinsFile();
      if (bfile != null) {
        uexpr = bfile.getElementNamed(referencedName);
      }
    }
    if (uexpr == null) {
      //uexpr = PyResolveUtil.resolveOffContext(this);
      uexpr = PyUtil.turnDirIntoInit(PyResolveUtil.scanOuterContext(new ResolveProcessor(referencedName), realContext));
    }
    if (uexpr != null) {
      ret.add(new ImportedResolveResult(uexpr, getRate(uexpr), processor.getDefiners()));
    }

    return ret;
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

  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiNamedElement) {
      final String elementName = ((PsiNamedElement)element).getName();
      if (Comparing.equal(myElement.getReferencedName(), elementName) || PyNames.INIT.equals(elementName)) {
        if (element instanceof PyParameter || element instanceof PyTargetExpression) {
          PyFunction functionContainingUs = PsiTreeUtil.getParentOfType(getElement(), PyFunction.class);
          PyFunction functionContainingThem = PsiTreeUtil.getParentOfType(element, PyFunction.class);
          if (functionContainingUs != null && functionContainingUs == functionContainingThem) {
            return true;
          }
        }
        final PsiElement resolveResult = resolve();
        if (resolveResult == element) {
          return true;
        }
        // TODO support nonlocal statement
        final PyGlobalStatement ourGlobal = PyGlobalStatementNavigator.getByArgument(resolveResult);
        final PyGlobalStatement theirGlobal = PyGlobalStatementNavigator.getByArgument(element);
        if (ourGlobal != null || theirGlobal != null) {
          PsiElement ourContainer = PsiTreeUtil.getParentOfType(getElement(), PsiNamedElement.class);
          PsiElement theirContainer = PsiTreeUtil.getParentOfType(element, PsiNamedElement.class);
          if (ourGlobal != null && ourContainer != null && PsiTreeUtil.isAncestor(theirContainer, ourContainer, false)) {
            return true;            
          }
          if (theirGlobal != null && theirContainer != null && PsiTreeUtil.isAncestor(ourContainer, theirContainer, false)) {
            return true;            
          }
        }
        return false; // TODO: handle multi-resolve
      }
    }
    return false;
  }

  private static final Condition<PsiElement> IS_STAR_IMPORT = new Condition<PsiElement>() {
    public boolean value(PsiElement psiElement) {
      return psiElement instanceof PyStarImportElement;
    }
  };

  @NotNull
  public Object[] getVariants() {
    final List<Object> ret = new ArrayList<Object>();

    // Use real context here to enable correct completion and resolve in case of PyExpressionCodeFragment!!!
    final PsiElement realContext = PyPsiUtils.getRealContext(myElement);

    // include our own names
    final int underscores = PyUtil.getInitialUnderscores(myElement.getName());
    final PyUtil.UnderscoreFilter filter = new PyUtil.UnderscoreFilter(underscores);
    final VariantsProcessor processor = new VariantsProcessor(myElement, null, filter);
    PyResolveUtil.treeCrawlUp(processor, realContext); // names from here
    PyResolveUtil.scanOuterContext(processor, realContext); // possible names from around us at call time

    // in a call, include function's arg names
    PyCallExpression call_expr = PsiTreeUtil.getParentOfType(myElement, PyCallExpression.class);
    if (call_expr != null) {
      PyExpression callee = call_expr.getCallee();
      if (callee instanceof PyReferenceExpression) {
        if (PsiTreeUtil.getParentOfType(myElement, PyKeywordArgument.class) == null) {
          PsiElement def = ((PyReferenceExpression)callee).getReference().resolve();
          if (def instanceof PyFunction) {
            addKeywordArgumentVariants((PyFunction)def, ret);
          }
          else if (def instanceof PyClass) {
            PyFunction init = ((PyClass)def).findMethodByName(PyNames.INIT, true);  // search in superclasses
            if (init != null) {
              addKeywordArgumentVariants(init, ret);
            }
          }
        }
      }
    }

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
          PyResolveUtil.treeCrawlUp(processor, true, import_src.getReference().resolve()); // names from that module
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

  private static void addKeywordArgumentVariants(PyFunction def, final List<Object> ret) {
    addKeywordArgumentVariants(def, ret, new HashSet<PyFunction>());
  }

  private static void addKeywordArgumentVariants(PyFunction def, List<Object> ret, Collection<PyFunction> visited) {
    if (visited.contains(def)) {
      return;
    }
    visited.add(def);
    final Set<PyFunction.Flag> flags = def.getContainingClass() != null ? PyUtil.detectDecorationsAndWrappersOf(def) : null;
    final KwArgParameterCollector collector = new KwArgParameterCollector(flags, ret);
    def.getParameterList().acceptChildren(collector);
    if (collector.hasKwArgs()) {
      KwArgFromStatementCallCollector fromStatementCallCollector = new KwArgFromStatementCallCollector(ret, collector.getKwArgs());
      def.getStatementList().acceptChildren(fromStatementCallCollector);

      //if (collector.hasOnlySelfAndKwArgs()) {
      // nothing interesting besides self and **kwargs, let's look at superclass (PY-778)
      if (fromStatementCallCollector.isKwArgsTransit()) {

        final PsiElement superMethod = PySuperMethodsSearch.search(def).findFirst();
        if (superMethod instanceof PyFunction) {
          addKeywordArgumentVariants((PyFunction)superMethod, ret, visited);
        }
      }
    }
//}
  }

  private static class KwArgParameterCollector extends PyElementVisitor {
    private int myCount;
    private final Set<PyFunction.Flag> myFlags;
    private final List<Object> myRet;
    private boolean myHasSelf = false;
    private boolean myHasKwArgs = false;
    private PyParameter kwArgsParam = null;

    public KwArgParameterCollector(Set<PyFunction.Flag> flags, List<Object> ret) {
      myFlags = flags;
      myRet = ret;
    }

    @Override
    public void visitPyParameter(PyParameter par) {
      myCount++;
      if (myCount == 1 && myFlags != null && !myFlags.contains(PyFunction.Flag.STATICMETHOD)) {
        myHasSelf = true;
        return;
      }
      PyNamedParameter namedParam = par.getAsNamed();
      assert namedParam != null;
      if (!namedParam.isKeywordContainer() && !namedParam.isPositionalContainer()) {
        final LookupElement item = PyUtil.createNamedParameterLookup(namedParam.getName());
        myRet.add(item);
      }
      else if (namedParam.isKeywordContainer()) {
        myHasKwArgs = true;
        kwArgsParam = namedParam;
      }
    }

    public PyParameter getKwArgs() {
      return kwArgsParam;
    }

    public boolean hasKwArgs() {
      return myHasKwArgs;
    }

    public boolean hasOnlySelfAndKwArgs() {
      return myCount == 2 && myHasSelf && myHasKwArgs;
    }
  }

  private static class KwArgFromStatementCallCollector extends PyElementVisitor {
    private final List<Object> myRet;
    private final PyParameter myKwArgs;
    private boolean kwArgsTransit = true;

    public KwArgFromStatementCallCollector(List<Object> ret, @NotNull PyParameter kwArgs) {
      myRet = ret;
      this.myKwArgs = kwArgs;
    }

    @Override
    public void visitPyElement(PyElement node) {
      node.acceptChildren(this);
    }

    @Override
    public void visitPySubscriptionExpression(PySubscriptionExpression node) {
      String operandName = node.getOperand().getName();
      processGet(operandName, node.getIndexExpression());
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      if (node.isCalleeText("pop", "get", "getattr")) {
        PyReferenceExpression child = PsiTreeUtil.getChildOfType(node.getCallee(), PyReferenceExpression.class);
        if (child != null) {
          String operandName = child.getName();
          if (node.getArguments().length > 0) {
            PyExpression argument = node.getArguments()[0];
            processGet(operandName, argument);
          }
        }
      }
      else if (node.isCalleeText("__init__")) {
        kwArgsTransit = false;
        for (PyExpression e : node.getArguments()) {
          if (e instanceof PyStarArgument) {
            PyStarArgument kw = (PyStarArgument)e;
            if (Comparing.equal(myKwArgs.getName(), kw.getFirstChild().getNextSibling().getText())) {
              kwArgsTransit = true;
              break;
            }
          }
        }
      }
      super.visitPyCallExpression(node);
    }

    private void processGet(String operandName, PyExpression argument) {
      if (Comparing.equal(myKwArgs.getName(), operandName) &&
          argument instanceof PyStringLiteralExpression) {
        String name = ((PyStringLiteralExpression)argument).getStringValue();
        if (PyUtil.isPythonIdentifier(name)) {
          myRet.add(PyUtil.createNamedParameterLookup(name));
        }
      }
    }

    /**
     * is name of kwargs parameter the same as transmitted to __init__ call
     * @return
     */
    public boolean isKwArgsTransit() {
      return kwArgsTransit;
    }
  }

  public boolean isSoft() {
    return false;
  }

  public HighlightSeverity getUnresolvedHighlightSeverity() {
    if (isBuiltInConstant()) return null;
    final PyExpression qualifier = myElement.getQualifier();
    if (qualifier == null) {
      return HighlightSeverity.ERROR;
    }
    if (qualifier.getType(TypeEvalContext.fast()) != null) {
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
}
