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

package com.jetbrains.python.psi;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.impl.PyScopeProcessor;
import com.jetbrains.python.psi.impl.ResolveImportUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 14.06.2005
 * Time: 23:45:32
 * To change this template use File | Settings | File Templates.
 */
public class PyResolveUtil {


  @NotNull
  public static String getReadableRepr(PsiElement elt) {
    if (elt == null) return "null!";
    ASTNode node = elt.getNode();
    if (node == null) return "null";
    else {
      String s = node.getText(); 
      int cut_pos = s.indexOf('\n');
      if (cut_pos < 0)  cut_pos = s.length();
      return s.substring(0, java.lang.Math.min(cut_pos, s.length()));
    }
  }

  private PyResolveUtil() {
  }


  /**
   * Tries to find nearest parent that conceals nams definded inside it. Such elements are 'class' and 'def':
   * anything defined within it does not seep to the namespace below them, but is concealed within.
   * @param elt starting point of search.
   * @return 'class' or 'def' element, or null if not found.
   */
  @Nullable
  public static PsiElement getConcealingParent(PsiElement elt) {
    PsiElement top = PsiTreeUtil.getParentOfType(elt, PyClass.class, PyFunction.class);
    return top;
  }

  protected static PsiElement getInnermostChildOf(PsiElement elt) {
    PsiElement feeler = elt;
    PsiElement seeker;
    seeker = feeler;
    // find innermost last child of the subtree we're in
    while (feeler != null) {
      seeker = feeler;
      feeler = feeler.getLastChild();
    }
    return seeker;
  }

  /**
   * Returns closest previous node of given class, as input file would have it.
   * @param elt node from which to look for a previous atatement.
   * @param cls class of the previous node to find.
   * @return previous statement, or null.
   */
  @Nullable
  public static <T> T getPrevNodeOf(PsiElement elt, Class<T> cls) {
    PsiElement seeker = elt;
    while (seeker != null) {
      PsiElement feeler = seeker.getPrevSibling();
      if (feeler != null) {
        seeker = getInnermostChildOf(feeler);
      }
      else { // we were the first subnode
        // find something above the parent node we've not exhausted yet
        seeker = seeker.getParent();
        if (seeker instanceof PyFile) return null; // all file nodes have been looked up, in vain
      }
      if (cls.isInstance(seeker)) return (T)seeker;
    }
    // here elt is null or a PsiFile is not up in the parent chain.
    return null;
  }

  /**
   * Crawls up the PSI tree, checking nodes as if crawling backwards through source lexemes.
   * @param processor a visitor that says when the crawl is done and collects info.
   * @param elt element from which we start (not checked by processor); if null, the search immediately fails. 
   * @param fromunder if true, search not above elt, but from an [possibly imaginary] node right below elt; so elt gets analyzed, too.
   * @return first element that the processor accepted.
   */
  @Nullable
  public static PsiElement treeCrawlUp(PsiScopeProcessor processor, PsiElement elt, boolean fromunder) {
    if (elt == null) return null; // can't find anyway.
    PsiElement seeker = elt;
    PsiElement cap = getConcealingParent(elt);
    do {
      if (fromunder) {
        fromunder = false; // only honour fromunder once per call
        seeker = getPrevNodeOf(getInnermostChildOf(seeker), NameDefiner.class);
      }
      else { // main case
        seeker = getPrevNodeOf(seeker, NameDefiner.class);
      }
      // aren't we in the same defining assignment, global, etc?
      if ((seeker != null) && ((NameDefiner)seeker).mustResolveOutside() && PsiTreeUtil.isAncestor(seeker, elt, true)
      ) {
        seeker = getPrevNodeOf(seeker, NameDefiner.class);
      }
      // maybe we're under cap
      while (true) {
        PsiElement local_cap = getConcealingParent(seeker);
        if (
            (local_cap != null) && (local_cap != cap) && // elt and seeker are under different caps and...
            ((cap == null) || !PsiTreeUtil.isAncestor(local_cap, cap, true)) // elt's cap is not under local cap 
        ) { // only look at local cap and above
          if (local_cap instanceof NameDefiner) seeker = local_cap;
          else seeker = getPrevNodeOf(local_cap, NameDefiner.class);
        }
        if (local_cap == null) break; // seeker is in global context
        if (local_cap == cap) break; // seeker is in the same context as elt
        if ((cap != null) && PsiTreeUtil.isAncestor(local_cap, cap, true)) break; // seeker is in a context above elt's
      }
      // maybe we're capped by a class
      PsiElement possible_class_cap = getConcealingParent(seeker);
      if (possible_class_cap instanceof PyClass) continue; // class implicitly qualifies things, and we're looking for unqualified.
      // check
      if (seeker != null) {
        if (!processor.execute(seeker, ResolveState.initial())) {
          if (processor instanceof ResolveProcessor) {
            return ((ResolveProcessor)processor).getResult();
          }
          else return seeker; // can't point to exact element, but somewhere here
        }
      }
    } while (seeker != null);
    return null;
  }

  /**
   * Returns treeCrawlUp(processor, elt, false). A convenience method.
   * @see com.jetbrains.python.psi.PyResolveUtil#treeCrawlUp(PsiScopeProcessor, PsiElement, boolean)
   */
  @Nullable
  public static PsiElement treeCrawlUp(PsiScopeProcessor processor, PsiElement elt) {
    return treeCrawlUp(processor, elt, false);
  }


  /**
   * Tries to match two [qualified] reference expression paths; target must be a 'sublist' of source to match.
   * E.g., 'a.b.c.d' and 'a.b.c' would match, while 'a.b.c' and 'a.b.c.d' would not. Eqaully, 'a.b.c' and 'a.b.d' would not match.
   * If either source or target is null, false is returned.
   * @see #unwindRefPath(PyReferenceExpression).
   * @param source_path expression path to match (the longer list of qualifiers).
   * @param target_path expression path to match against (hopeful sublist of qualifiers of source).
   * @return true if source matches target.
   */
  public static boolean matchPaths(List<PyReferenceExpression> source_path, List<PyReferenceExpression> target_path) {
    // turn qualifiers into lists
    if ((source_path == null) || (target_path == null)) return false;
    // compare until target is exhausted
    Iterator<PyReferenceExpression> source_iter = source_path.iterator();
    for (final PyReferenceExpression target_elt : target_path) {
      if (source_iter.hasNext()) {
        PyReferenceExpression source_elt = source_iter.next();
        if (!target_elt.getText().equals(source_elt.getText())) return false;
      }
      else return false; // source exhausted before target
    }
    return true;
  }

  /**
   * Unwinds a [multi-level] qualified expression into a path, as seen in source text, i.e. outermost qualifier first.
   * If any qualifier happens to be not a reference expression, or expr is null, null is returned.
   * @param expr an experssion to unwind.
   * @return path as a list of ref expressions, or null.
   */
  @Nullable
  public static List<PyReferenceExpression> unwindRefPath(final PyReferenceExpression expr) {
    final List<PyReferenceExpression> path = new LinkedList<PyReferenceExpression>();
    PyExpression maybe_step;
    PyReferenceExpression step = expr;
    try {
      while (step != null) {
        path.add(0, step);
        maybe_step = step.getQualifier();
        step = (PyReferenceExpression)maybe_step;
      }
    }
    catch (ClassCastException e) {
      return null;
    }
    return path;
  }

  public static class ResolveProcessor implements PyScopeProcessor {
    private String myName;
    private PsiElement myResult = null;
    private Set<String> mySeen;

    public ResolveProcessor(final String name) {
      myName = name;
      mySeen = new HashSet<String>();
    }

    public PsiElement getResult() {
      return myResult;
    }
    
    static String _nvl(Object s) {
      if (s != null) return "'" + s.toString() + "'";
      else return "null";
    }
    
    public Set<String> getSeen() {
      return mySeen;
    }
    
    public String toString() {
      return _nvl(myName) + ", " + _nvl(myResult);
    }
    
    public boolean execute(PsiElement element, ResolveState substitutor) {
      if (element instanceof PyFile) {
        final VirtualFile file = ((PyFile)element).getVirtualFile();
        if (file != null) {
          if (myName.equals(file.getNameWithoutExtension())) {
            myResult = element;
            return false;
          }
          else if (ResolveImportUtil.INIT_PY.equals(file.getName())) {
            VirtualFile dir = file.getParent();
            if ((dir != null) && myName.equals(dir.getName())) {
              myResult = element;
              return false;
            }
          }
        }
      }
      else if (element instanceof PsiNamedElement) {
        if (myName.equals(((PsiNamedElement)element).getName())) {
          myResult = element;
          return false;
        }
      }
      else if (element instanceof PyReferenceExpression) {
        PyReferenceExpression expr = (PyReferenceExpression)element;
        String referencedName = expr.getReferencedName();
        if (referencedName != null && referencedName.equals(myName)) {
          myResult = element;
          return false;
        }
      }
      else if (element instanceof NameDefiner) {
        final NameDefiner definer = (NameDefiner)element;
        PsiElement by_name = definer.getElementNamed(myName);
        if (by_name != null) {
          myResult = by_name;
          return false;
        }
      }

      return true;
    }

    public boolean execute(final PsiElement element, final String asName) {
      if (asName.equals(myName)) {
        myResult = element;
        return false;
      }
      return true;
    }

    @Nullable
    public <T> T getHint(Class<T> hintClass) {
      return null;
    }

    public void handleEvent(Event event, Object associated) {
    }

    /**
     * Looks at an element and says if looking at it worthy.
     * Used to break circular attempts to resolve names imported into __init__.py inside it again.
     * @param element to be analyzed and probably remembered.
     * @return true if execute() may be tried with this element; else treeWalkUp and the like should immediately return negative result.
     */
    public boolean approve(PsiElement element) {
      if ((element instanceof PyFile) && (ResolveImportUtil.INIT_PY.equals(((PyFile)element).getName()))) {
        String fname = ((PyFile)element).getUrl();
        if (mySeen.contains(fname)) return false; // already seen it, may not try again
        else mySeen.add(fname);
      }
      return true;
    }
  }

  public static class MultiResolveProcessor implements PsiScopeProcessor {
    private String _name;
    private List<ResolveResult> _results = new ArrayList<ResolveResult>();

    public MultiResolveProcessor(String name) {
      _name = name;
    }

    public ResolveResult[] getResults() {
      return _results.toArray(new ResolveResult[_results.size()]);
    }

    public boolean execute(PsiElement element, ResolveState substitutor) {
      if (element instanceof PsiNamedElement) {
        if (_name.equals(((PsiNamedElement)element).getName())) {
          _results.add(new PsiElementResolveResult(element));
        }
      }
      else if (element instanceof PyReferenceExpression) {
        PyReferenceExpression expr = (PyReferenceExpression)element;
        String referencedName = expr.getReferencedName();
        if (referencedName != null && referencedName.equals(_name)) {
          _results.add(new PsiElementResolveResult(element));
        }
      }

      return true;
    }

    public <T> T getHint(Class<T> hintClass) {
      return null;
    }

    public void handleEvent(Event event, Object associated) {
    }
  }

  public static class VariantsProcessor implements PsiScopeProcessor {
    private Map<String, LookupElement> myVariants = new HashMap<String, LookupElement>();

    public LookupElement[] getResult() {
      final Collection<LookupElement> variants = myVariants.values();
      return variants.toArray(new LookupElement[variants.size()]);
    }

    public boolean execute(PsiElement element, ResolveState substitutor) {
      // TODO: refactor to look saner; much code duplication
      if (element instanceof PsiNamedElement) {
        final PsiNamedElement psiNamedElement = (PsiNamedElement)element;
        final String name = psiNamedElement.getName();
        if (!myVariants.containsKey(name)) {
          myVariants.put(name, LookupElementFactory.getInstance().createLookupElement(psiNamedElement));
        }
      }
      else if (element instanceof PyReferenceExpression) {
        PyReferenceExpression expr = (PyReferenceExpression)element;
        String referencedName = expr.getReferencedName();
        if (referencedName != null && !myVariants.containsKey(referencedName)) {
          myVariants.put(referencedName, LookupElementFactory.getInstance().createLookupElement(element, referencedName));
        }
      }
      else if (element instanceof NameDefiner) {
        final NameDefiner definer = (NameDefiner)element;
        for (PyElement expr: definer.iterateNames()) {
          if (expr != null) { // NOTE: maybe rather have SingleIterables skip nulls outright?
            String referencedName = expr.getName();
            if (referencedName != null && !myVariants.containsKey(referencedName)) {
              myVariants.put(referencedName, LookupElementFactory.getInstance().createLookupElement(element, referencedName));
            }
          }
        }
      }

      return true;
    }

    @Nullable
    public <T> T getHint(Class<T> hintClass) {
      return null;
    }

    public void handleEvent(Event event, Object associated) {
    }
  }
}
