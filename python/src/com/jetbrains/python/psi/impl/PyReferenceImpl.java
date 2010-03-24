package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.SortedList;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyReferenceImpl implements PsiReferenceEx, PsiPolyVariantReference {
  private final PyReferenceExpressionImpl myElement;

  public PyReferenceImpl(PyReferenceExpressionImpl element) {
    myElement = element;
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
    return results.length >= 1 ? results[0].getElement() : null;
  }

  private static boolean USE_CACHE = true; // change to false in debug time to switch off caching

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

  private static class ResultList extends ArrayList<RatedResolveResult> {
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
  private List<RatedResolveResult> resolveInner() {
    ResultList ret = new ResultList();

    final String referencedName = myElement.getReferencedName();
    if (referencedName == null) return ret;

    // Handle import reference
    if (PsiTreeUtil.getParentOfType(myElement, PyImportElement.class, PyFromImportStatement.class) != null) {
      PsiElement target = ResolveImportUtil.resolveImportReference(myElement);

      target = PyUtil.turnDirIntoInit(target);
      if (target == null) {
        ret.clear();
        return ret; // it was a dir without __init__.py, worthless
      }
      ret.poke(target, RatedResolveResult.RATE_HIGH);
      return ret;
    }

    final PyExpression qualifier = myElement.getQualifier();
    if (qualifier != null) {
      return resolveQualifiedReference(ret, referencedName, qualifier);
    }

    // here we have an unqualified expr. it may be defined:
    // ...in current file
    ResolveProcessor processor = new ResolveProcessor(referencedName);
    PsiElement uexpr = PyResolveUtil.treeCrawlUp(processor, false, myElement, myElement.getContainingFile());
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
      PyType otype = PyBuiltinCache.getInstance(myElement).getObjectType(); // "object" as a closest kin to "module"
      if (otype != null) uexpr = otype.resolveMember(myElement.getName());
    }
    if (uexpr == null) {
      // ...as a builtin symbol
      PyFile bfile = PyBuiltinCache.getInstance(myElement).getBuiltinsFile();
      if (bfile != null) {
        uexpr = bfile.getElementNamed(referencedName);
      }
    }
    if (uexpr == null) {
      //uexpr = PyResolveUtil.resolveOffContext(this);
      uexpr = PyResolveUtil.scanOuterContext(new ResolveProcessor(referencedName), myElement);
    }
    uexpr = PyUtil.turnDirIntoInit(uexpr); // treeCrawlUp might have found a dir
    if (uexpr != null) ret.poke(uexpr, getRate(uexpr));
    return ret;
  }

  private List<RatedResolveResult> resolveQualifiedReference(final ResultList ret, final String referencedName, final PyExpression qualifier) {
    // regular attributes
    PyType qualifierType = qualifier.getType();
    if (qualifierType != null) {
      if (qualifier instanceof PyQualifiedExpression) {
        // enrich the type info with any fields assigned nearby
        List<PyQualifiedExpression> qualifier_path = PyResolveUtil.unwindQualifiers((PyQualifiedExpression)qualifier);
        if (qualifier_path != null) {
          for (PyExpression ex : collectAssignedAttributes((PyQualifiedExpression)qualifier)) {
            if (referencedName.equals(ex.getName())) {
              ret.poke(ex, RatedResolveResult.RATE_NORMAL);
              return ret;
            }
          }
        }
      }
      // resolve within the type proper
      PsiElement ref_elt = PyUtil.turnDirIntoInit(qualifierType.resolveMember(referencedName));
      if (ref_elt != null) ret.poke(ref_elt, RatedResolveResult.RATE_NORMAL);
    }
    // special case of __doc__
    if ("__doc__".equals(referencedName)) {
      PsiElement docstring = null;
      if (qualifierType instanceof PyClassType) {
        PyClass qual_class = ((PyClassType)qualifierType).getPyClass();
        if (qual_class != null) docstring = qual_class.getDocStringExpression();
      }
      else if (qualifierType instanceof PyModuleType) {
        PsiFile qual_module = ((PyModuleType)qualifierType).getModule();
        if (qual_module instanceof PyDocStringOwner) {
          docstring = ((PyDocStringOwner)qual_module).getDocStringExpression();
        }
      }
      else if (qualifier instanceof PyReferenceExpression) {
        PsiElement qual_object = ((PyReferenceExpression)qualifier).getReference().resolve();
        if (qual_object instanceof PyDocStringOwner) {
          docstring = ((PyDocStringOwner)qual_object).getDocStringExpression();
        }
      }
      if (docstring != null) {
        ret.poke(docstring, RatedResolveResult.RATE_HIGH);
      }
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

  private static Collection<PyExpression> collectAssignedAttributes(PyQualifiedExpression qualifier) {
    List<PyQualifiedExpression> qualifier_path = PyResolveUtil.unwindQualifiers(qualifier);
    if (qualifier_path != null) {
      AssignmentCollectProcessor proc = new AssignmentCollectProcessor(qualifier_path);
      PyResolveUtil.treeCrawlUp(proc, qualifier);
      return proc.getResult();
    }
    else {
      return Collections.emptyList();
    }
  }

  public String getCanonicalText() {
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    ASTNode nameElement = myElement.getNameElement();
    if (nameElement != null) {
      final ASTNode newNameElement = PythonLanguage.getInstance().getElementGenerator().createNameIdentifier(myElement.getProject(), newElementName);
      myElement.getNode().replaceChild(nameElement, newNameElement);
    }
    return myElement;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiNamedElement) {
      if (Comparing.equal(myElement.getReferencedName(), ((PsiNamedElement)element).getName())) {
        return resolve() == element; // TODO: handle multi-resolve
      }
    }
    return false;
  }


  private static final Object[] NO_VARIANTS = ArrayUtil.EMPTY_OBJECT_ARRAY;

  @NotNull
  public Object[] getVariants() {
    // qualifier limits the namespace
    final PyExpression qualifier = myElement.getQualifier();
    if (qualifier != null) {
      PyType qualifierType = qualifier.getType();
      ProcessingContext ctx = new ProcessingContext();
      final Set<String> names_already = new HashSet<String>();
      ctx.put(PyType.CTX_NAMES, names_already);
      if (qualifierType != null) {
        Collection<Object> variants = new ArrayList<Object>();
        if (qualifier instanceof PyQualifiedExpression) {
          Collection<PyExpression> attrs = collectAssignedAttributes((PyQualifiedExpression)qualifier);
          variants.addAll(attrs);
          for (PyExpression ex : attrs) {
            if (ex instanceof PyReferenceExpression) {
              PyReferenceExpression refex = (PyReferenceExpression)ex;
              names_already.add(refex.getReferencedName());
            }
            else if (ex instanceof PyTargetExpression) {
              PyTargetExpression targetExpr = (PyTargetExpression) ex;
              names_already.add(targetExpr.getName());
            }
          }
          Collections.addAll(variants, qualifierType.getCompletionVariants(myElement, ctx));
          return variants.toArray();
        }
        else {
          return qualifierType.getCompletionVariants(myElement, ctx);
        }
      }
      return NO_VARIANTS;
    }

    // imports are another special case
    if (PsiTreeUtil.getParentOfType(myElement, PyImportElement.class, PyFromImportStatement.class) != null) {
      // complete to possible modules
      return ResolveImportUtil.suggestImportVariants(myElement);
    }

    final List<Object> ret = new ArrayList<Object>();

    // include our own names
    final VariantsProcessor processor = new VariantsProcessor();
    PyResolveUtil.treeCrawlUp(processor, myElement); // names from here
    PyResolveUtil.scanOuterContext(processor, myElement); // possible names from around us at call time

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
    CollectProcessor collect_proc;
    collect_proc = new CollectProcessor(PyStarImportElement.class);
    PyResolveUtil.treeCrawlUp(collect_proc, myElement);
    List<PsiElement> stars = collect_proc.getResult();
    for (PsiElement star_elt : stars) {
      final PyFromImportStatement from_import_stmt = (PyFromImportStatement)star_elt.getParent();
      if (from_import_stmt != null) {
        final PyReferenceExpression import_src = from_import_stmt.getImportSource();
        if (import_src != null) {
          processor.setNotice(import_src.getName());
          PyResolveUtil.treeCrawlUp(processor, true, import_src.getReference().resolve()); // names from that module
        }
      }
    }
    // include builtin names
    processor.setNotice("__builtin__");
    PyResolveUtil.treeCrawlUp(processor, true, PyBuiltinCache.getInstance(getElement()).getBuiltinsFile()); // names from __builtin__

    // if we're a normal module, add module's attrs
    PsiFile f = myElement.getContainingFile();
    if (f instanceof PyFile) {
      for (String name : PyModuleType.getPossibleInstanceMembers()) {
        ret.add(LookupElementBuilder.create(name).setIcon(Icons.FIELD_ICON));
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
    if (qualifier.getType() != null) {
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

    @Nullable
    public ResolveResult[] resolve(final PyReferenceImpl ref, final boolean incompleteCode) {
      return ref.multiResolveInner(incompleteCode);
    }
  }
}
