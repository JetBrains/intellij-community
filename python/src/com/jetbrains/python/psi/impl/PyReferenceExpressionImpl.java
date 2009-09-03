/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.SortedList;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Implements reference expression PSI.
 * User: yole
 * Date: 29.05.2005
 */
public class PyReferenceExpressionImpl extends PyElementImpl implements PyReferenceExpression {

  public PyReferenceExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PsiElement getElement() {
    return this;
  }

  @NotNull
  public PsiReference[] getReferences() {
    List<PsiReference> refs = new ArrayList<PsiReference>(Arrays.asList(super.getReferences()));
    refs.add(this);
    return refs.toArray(new PsiReference[refs.size()]);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyReferenceExpression(this);
  }

  @PsiCached
  public
  @Nullable
  PyExpression getQualifier() {
    final ASTNode[] nodes = getNode().getChildren(PyElementTypes.EXPRESSIONS);
    return (PyExpression)(nodes.length == 1 ? nodes[0].getPsi() : null);
  }

  public TextRange getRangeInElement() {
    final ASTNode nameElement = getNameElement();
    final int startOffset = nameElement != null ? nameElement.getStartOffset() : getNode().getTextRange().getEndOffset();
    return new TextRange(startOffset - getNode().getStartOffset(), getTextLength());
  }

  @PsiCached
  public
  @Nullable
  String getReferencedName() {
    final ASTNode nameElement = getNameElement();
    return nameElement != null ? nameElement.getText() : null;
  }

  @PsiCached
  private
  @Nullable
  ASTNode getNameElement() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Nullable
  @Override
  public String getName() {
    return getReferencedName();
  }


  /**
   * Resolves reference to the most obvious point.
   * Imported module names: to module file (or directory for a qualifier). 
   * Other identifiers: to most recent definition before this reference.
   * This implementation is cached.
   * @see #resolveInner().
  **/
  public
  @Nullable
  PsiElement resolve() {
    final ResolveResult[] results = multiResolve(false);
    return results.length == 1 ? results[0].getElement() : null;
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
   * If argument is a PsiDirectory, turn it into a PsiFile that points to __init__.py in that directory.
   * If there's no __init__.py there, null is returned, there's no point to resolve to a dir which is not a package.
   * Alas, resolve() and multiResolve() can't return anything but a PyFile or PsiFileImpl.isPsiUpToDate() would fail.
   * This is because isPsiUpToDate() relies on identity of objects returned by FileViewProvider.getPsi().
   * If we ever need to exactly tell a dir from __init__.py, that logic has to change.
   * @param target a resolve candidate.
   * @return a PsiFile if target was a PsiDirectory, or null, or target unchanged.
   */
  private PsiElement turnDirIntoInit(PsiElement target) {
    if (target instanceof PsiDirectory) {
      final PsiDirectory dir = (PsiDirectory)target;
      final PsiFile file = dir.findFile(ResolveImportUtil.INIT_PY);
      if (file != null) {
        file.putCopyableUserData(PyFile.KEY_IS_DIRECTORY, Boolean.TRUE);
        return file; // ResolveImportUtil will extract directory part as needed, everyone else are better off with a file.
      }
      else return null; // dir without __init__.py does not resolve
    }
    else return target;
  }

  /**
   * Does actual resolution of resolve().
   * @return resolution result.
   * @see #resolve()
   */
  private
  @NotNull
  List<RatedResolveResult> resolveInner() {
    //List<PsiElement> ret = new ArrayList<PsiElement>();
    ResultList ret = new ResultList();

    final String referencedName = getReferencedName();
    if (referencedName == null) return ret;

    if (PsiTreeUtil.getParentOfType(this, PyImportElement.class, PyFromImportStatement.class) != null) {
      PsiElement target = ResolveImportUtil.resolveImportReference(this);

      target = turnDirIntoInit(target);
      if (target == null) {
        ret.clear();
        return ret; // it was a dir without __init__.py, worthless
      }
      ret.poke(target, RatedResolveResult.RATE_HIGH);
      return ret;
    }

    final PyExpression qualifier = getQualifier();
    if (qualifier != null) {
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
        PsiElement ref_elt = qualifierType.resolveMember(referencedName);
        if (ref_elt != null) ret.poke(ref_elt, RatedResolveResult.RATE_NORMAL);
        return ret;
      }
      return ret;
    }

    // here we have an unqualified expr. it may be defined:
    // ...in current file
    ResolveProcessor processor = new ResolveProcessor(referencedName);
    PsiElement uexpr = PyResolveUtil.treeCrawlUp(processor, this);
    if ((uexpr != null)) {
      if ((uexpr instanceof PyClass)) {
        // is it a case of the bizarre "class Foo(Foo)" construct?
        PyClass cls = (PyClass)uexpr;
        for (PyExpression base_expr : cls.getSuperClassExpressions()){
          if (base_expr == this) {
            ret.clear();
            return ret; // cannot resolve us, the base class ref, to the class being defined
          }
        }
      }
      // sort what we got
      for (NameDefiner hit : processor.getDefiners()) {
        ret.poke(hit, getRate(hit));
      }
    }
    if (uexpr == null) {
      // ...as a part of current module
      PyType otype = PyBuiltinCache.getInstance(getProject()).getObjectType(); // "object" as a closest kin to "module"
      if (otype != null) uexpr = otype.resolveMember(getName());
    }
    if (uexpr == null) {
      // ...as a builtin symbol
      PyFile bfile = PyBuiltinCache.getInstance(getProject()).getBuiltinsFile();
      uexpr = PyResolveUtil.treeCrawlUp(new ResolveProcessor(referencedName), true, bfile);
    }
    if (uexpr == null) {
      uexpr = PyResolveUtil.resolveOffContext(this);
    }
    uexpr = turnDirIntoInit(uexpr); // treeCrawlUp might have found a dir
    if (uexpr != null) ret.poke(uexpr, getRate(uexpr));
    return ret;
  }

  // NOTE: very crude
  private static int getRate(PsiElement elt) {
    int rate;
    if (elt instanceof PyImportElement || elt instanceof PyStarImportElement) rate = RatedResolveResult.RATE_LOW;
    else if (elt instanceof PyFile) rate = RatedResolveResult.RATE_HIGH;
    else rate = RatedResolveResult.RATE_NORMAL;
    return rate;
  }

  private static Collection<PyExpression> collectAssignedAttributes(PyQualifiedExpression qualifier) {
    List<PyQualifiedExpression> qualifier_path = PyResolveUtil.unwindQualifiers(qualifier);
    if (qualifier_path != null) {
      AssignmentCollectProcessor proc = new AssignmentCollectProcessor(qualifier_path)
      ;
      PyResolveUtil.treeCrawlUp(proc, qualifier);
      return proc.getResult();
    }
    else return EMPTY_LIST; 
  }

  // sorts and modifies results of resolveInner
  private ResolveResult[] multiResolveInner(boolean incomplete) {
    final String referencedName = getReferencedName();
    if (referencedName == null) return ResolveResult.EMPTY_ARRAY;

    List<RatedResolveResult> targets = resolveInner();
    if (targets.size() == 0) return ResolveResult.EMPTY_ARRAY;

    // change class results to constructor results if there are any
    if (getParent() instanceof PyCallExpression) { // we're a call
      ListIterator<RatedResolveResult> it = targets.listIterator();
      while (it.hasNext()) {
        final RatedResolveResult rrr = it.next();
        final PsiElement elt = rrr.getElement();
        if (elt instanceof PyClass) {
          PyClass cls = (PyClass)elt;
          PyFunction init = cls.findMethodByName(PyNames.INIT);
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
              init = ancestor.findMethodByName(PyNames.INIT);
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


  public String getCanonicalText() {
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    ASTNode nameElement = getNameElement();
    if (nameElement != null) {
      final ASTNode newNameElement = getLanguage().getElementGenerator().createNameIdentifier(getProject(), newElementName);
      getNode().replaceChild(nameElement, newNameElement);
    }
    return this;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiNamedElement) {
      if (Comparing.equal(getReferencedName(), ((PsiNamedElement)element).getName())) {
        return resolve() == element; // TODO: handle multi-resolve
      }
    }
    return false;
  }

  
  private static final Object[] NO_VARIANTS = new Object[0];

  public Object[] getVariants() {
    // qualifier limits the namespace
    final PyExpression qualifier = getQualifier();
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
          }
          Collections.addAll(variants, qualifierType.getCompletionVariants(this, ctx));
          return variants.toArray();
        }
        else return qualifierType.getCompletionVariants(this, ctx);
      }
      return NO_VARIANTS;
    }

    // imports are another special case
    if (PsiTreeUtil.getParentOfType(this, PyImportElement.class, PyFromImportStatement.class) != null) {
      // complete to possible modules
      return ResolveImportUtil.suggestImportVariants(this);
    }

    final List<Object> ret = new ArrayList<Object>();
    final LookupElementFactory factory = LookupElementFactory.getInstance();

    // include our own names
    final VariantsProcessor processor = new VariantsProcessor();
    PyResolveUtil.treeCrawlUp(processor, this); // names from here

    // in a call, include function's arg names
    PyCallExpression call_expr = PsiTreeUtil.getParentOfType(this, PyCallExpression.class);
    if (call_expr != null) {
      PyExpression callee =call_expr.getCallee();
      if (callee instanceof PyReferenceExpression) {
        PsiElement def = ((PyReferenceExpression)callee).resolve();
        if (def instanceof PyFunction) {
          ((PyFunction)def).getParameterList().acceptChildren(
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
      }
    }

    // scan all "import *" and include names provided by them
    CollectProcessor collect_proc;
    collect_proc = new CollectProcessor(PyStarImportElement.class);
    PyResolveUtil.treeCrawlUp(collect_proc, this);
    List<PsiElement> stars = collect_proc.getResult();
    for (PsiElement star_elt : stars) {
      final PyFromImportStatement from_import_stmt = (PyFromImportStatement)star_elt.getParent();
      if (from_import_stmt != null) {
        final PyReferenceExpression import_src = from_import_stmt.getImportSource();
        if (import_src != null) {
          processor.setNotice(" | " + import_src.getName());
          PyResolveUtil.treeCrawlUp(processor, true, import_src.resolve()); // names from that module
        }
      }
    }
    // include builtin names
    processor.setNotice(" | __builtin__");
    PyResolveUtil.treeCrawlUp(processor, true, PyBuiltinCache.getInstance(getProject()).getBuiltinsFile()); // names from __builtin__

    // if we're a normal module, add module's attrs
    PsiFile f = getContainingFile();
    if (f instanceof PyFile) {
      for (String name : PyModuleType.getPossibleInstanceMembers()) {
        ret.add(LookupElementBuilder.create(name).setIcon(Icons.FIELD_ICON));
      }
    }

    /* TODO: move to a contributor that handles 'def' targets
    // if we're expanding a "__", include predefined __names__
    String name = getName();
    if (name != null && name.startsWith("__")) {
      List<LookupElement> result = processor.getResultList();
      LookupElementFactory factory = LookupElementFactory.getInstance();
      for (String s : PyNames.UnderscoredNames) {
        LookupItem item = (LookupItem)factory.createLookupElement(s);
        item.setAttribute(item.TAIL_TEXT_ATTR, " | predefined");
        item.setIcon(PyIcons.PREDEFINED);
        result.add(item);
      }
      ret.addAll(result);
    }
    */
    ret.addAll(processor.getResultList());
    return ret.toArray();
  }

  public boolean isSoft() {
    return false;
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    // in statements, process only the section in which the original expression was located
    PsiElement parent = getParent();
    if (parent instanceof PyStatement && lastParent == null && PsiTreeUtil.isAncestor(parent, place, true)) {
      return true;
    }

    // never resolve to references within the same assignment statement
    if (getParent() instanceof PyAssignmentStatement) {
      PsiElement placeParent = place.getParent();
      while (placeParent != null && placeParent instanceof PyExpression) {
        placeParent = placeParent.getParent();
      }
      if (placeParent == getParent()) {
        return true;
      }
    }

    if (this == place) return true;
    return processor.execute(this, substitutor);
  }

  public HighlightSeverity getUnresolvedHighlightSeverity() {
    if (isBuiltInConstant()) return null;
    final PyExpression qualifier = getQualifier();
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
    String name = getReferencedName();
    return PyNames.NONE.equals(name) || "True".equals(name) || "False".equals(name);
  }

  @Nullable
  public String getUnresolvedDescription() {
    return null;
  }

  @Override
  public String toString() {
    return "PyReferenceExpression: " + getReferencedName();
  }

  public PyType getType() {
    if (getQualifier() == null) {
      String name = getReferencedName();
      if (PyNames.NONE.equals(name)) {
        return PyNoneType.INSTANCE;
      }
    }
    ResolveResult[] targets = multiResolve(false);
    if (targets.length == 0) return null;
    PsiElement target = targets[0].getElement();
    if (target == this) {
      return null;
    }
    return getTypeFromTarget(target);
  }

  @Nullable
  public static PyType getTypeFromTarget(final PsiElement target) {
    if (target instanceof PyTargetExpression && PyNames.NONE.equals(((PyTargetExpression) target).getName())) {
      return PyNoneType.INSTANCE;
    }
    if (target instanceof PyFile) {
      return new PyModuleType((PyFile) target);
    }
    if (target instanceof PyExpression) {
      return ((PyExpression) target).getType();
    }
    if (target instanceof PyClass) {
      return new PyClassType((PyClass) target, true);
    }
    if (target instanceof PsiDirectory) {
      PsiFile file = ((PsiDirectory)target).findFile(ResolveImportUtil.INIT_PY);
      if (file != null) return getTypeFromTarget(file);
    }
    return getReferenceTypeFromProviders(target);
  }

  @Nullable
  public static PyType getReferenceTypeFromProviders(final PsiElement target) {
    for(PyTypeProvider provider: Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      final PyType result = provider.getReferenceType(target);
      if (result != null) return result;
    }

    return null;
  }

  // our very own caching resolver
  private static class CachingResolver implements ResolveCache.PolyVariantResolver<PyReferenceExpression> {

    public static CachingResolver INSTANCE = new CachingResolver();

    @Nullable
    public ResolveResult[] resolve(final PyReferenceExpression ref, final boolean incompleteCode) {
      assert ref instanceof PyReferenceExpressionImpl; 
      return ((PyReferenceExpressionImpl)ref).multiResolveInner(incompleteCode); // TODO: make it more aestetic?
    }

  }

}
