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
  protected static String _fmt_node(PsiElement elt) {
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

  @Nullable
  public static PsiElement treeWalkUp(PsiScopeProcessor processor, PsiElement elt, PsiElement lastParent, PsiElement place) {
    if (elt == null) return null;

    PsiElement cur = elt;
    do {
      if ((processor instanceof ResolveProcessor) && !(((ResolveProcessor)processor).approve(cur))) {
        return null;
      }
      if (!cur.processDeclarations(processor, ResolveState.initial(), cur == elt ? lastParent : null, elt)) {
        if (processor instanceof ResolveProcessor) {
          return ((ResolveProcessor)processor).getResult();
        }
      }
      if (cur instanceof PsiFile) break;
      cur = cur.getPrevSibling();
    }
    while (cur != null);

    if (elt == place) return null;

    return treeWalkUp(processor, elt.getContext(), elt, place);
  }

  // NOTE: to be moved to more general scope
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
   * If any qualifier happens to be not a referencce expression, or expr is null, null is returned.
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
        String path = ((PyFile)element).getVirtualFile().getPath(); // TODO: handle possible NPE
        if (mySeen.contains(path)) return false; // already seen it, may not try again
        else mySeen.add(path);
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
