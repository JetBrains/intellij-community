package com.jetbrains.python.psi.impl;

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
   * @see #resolveInner().
  **/
  @Nullable
  public PsiElement resolve() {
    final ResolveResult[] results = multiResolve(false);
   return results.length >= 1 && !(results [0] instanceof ImplicitResolveResult) ? results[0].getElement() : null;
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
   * @see com.intellij.psi.PsiPolyVariantReference#multiResolve(boolean)
  **/
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
            final PyFunction the_init = init;
            it.set(new RatedResolveResult(){
              public int getRate() { return rrr.getRate(); }

              public PsiElement getElement() { return the_init; }

              public boolean isValidResult() { return true; }
            });
          }
          else { // init not found; maybe it's ancestor's
            for (PyClass ancestor : cls.iterateAncestors()) {
              init = ancestor.findMethodByName(PyNames.INIT, false);
              if (init != null) {
                final PyFunction the_init = init;
                // add to resuls as low priority
                it.add(new RatedResolveResult(){
                  public int getRate() { return RATE_LOW; }

                  public PsiElement getElement() { return the_init; }

                  public boolean isValidResult() { return true; }
                });
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
      super.add(new RatedResolveResult() {
        public int getRate() { return rate; }

        public PsiElement getElement() { return what; }

        public boolean isValidResult() { return true; }
      });
      return true;
    }

    public void pokeAll(Collection<PsiElement> elts, int rate) {
      for (PsiElement elt : elts) poke(elt, rate);
    }
  }

  /**
   * Does actual resolution of resolve().
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
    PsiElement uexpr = PyResolveUtil.treeCrawlUp(processor, false, realContext, realContext.getContainingFile());
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
    if (uexpr == null) {
      // ...as a part of current module
      PyType otype = PyBuiltinCache.getInstance(realContext).getObjectType(); // "object" as a closest kin to "module"
      if (otype != null) uexpr = otype.resolveMember(myElement.getName());
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
      uexpr = PyResolveUtil.scanOuterContext(new ResolveProcessor(referencedName), realContext);
    }
    uexpr = PyUtil.turnDirIntoInit(uexpr); // treeCrawlUp might have found a dir
    if (uexpr != null) ret.poke(uexpr, getRate(uexpr));
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
          if (functionContainingUs == functionContainingThem) {
            return true;
          }
        }
        return resolve() == element; // TODO: handle multi-resolve
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
      PyExpression callee =call_expr.getCallee();
      if (callee instanceof PyReferenceExpression) {
        PsiElement def = ((PyReferenceExpression)callee).getReference().resolve();
        if (def instanceof PyFunction) {
          addKeywordArgumentVariants((PyFunction) def, ret);
        }
        else if (def instanceof PyClass) {
          PyFunction init = ((PyClass) def).findMethodByName(PyNames.INIT, true);  // search in superclasses
          if (init != null) {
            addKeywordArgumentVariants(init, ret);
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
    def.getParameterList().acceptChildren(
      new PyElementVisitor() {
        @Override
        public void visitPyParameter(PyParameter par) {
          PyNamedParameter n_param = par.getAsNamed();
          assert n_param != null;
          if (! n_param.isKeywordContainer() && ! n_param.isPositionalContainer()) {
            ret.add(LookupElementBuilder.create(n_param.getName() + "=").setIcon(n_param.getIcon(0)));
          }
        }
      }
    );
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

    @Nullable
    public ResolveResult[] resolve(final PyReferenceImpl ref, final boolean incompleteCode) {
      if (myNesting.get().getAndIncrement() >= 30) {
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
