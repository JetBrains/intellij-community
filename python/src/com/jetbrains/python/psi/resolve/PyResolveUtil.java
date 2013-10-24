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
package com.jetbrains.python.psi.resolve;

import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.impl.PyQualifiedNameFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

/**
 * Ref resolution routines.
 * User: dcheryasov
 * Date: 14.06.2005
 */
public class PyResolveUtil {

  private PyResolveUtil() {
  }


  /**
   * Returns closest previous node of given class, as input file would have it.
   *
   * @param elt          node from which to look for a previous statement.
   * @param elementTypes selected element types.
   * @return previous statement, or null.
   */
  @Nullable
  public static PsiElement getPrevNodeOf(PsiElement elt, TokenSet elementTypes) {
    ASTNode seeker = elt.getNode();
    while (seeker != null) {
      ASTNode feeler = seeker.getTreePrev();
      if (feeler != null &&
          (feeler.getElementType() == PyElementTypes.FUNCTION_DECLARATION ||
           feeler.getElementType() == PyElementTypes.CLASS_DECLARATION) &&
          elementTypes.contains(feeler.getElementType())) {
        return feeler.getPsi();
      }
      if (feeler != null) {
        seeker = TreeUtil.getLastChild(feeler);
      }
      else { // we were the first subnode
        // find something above the parent node we've not exhausted yet
        seeker = seeker.getTreeParent();
        if (seeker instanceof FileASTNode) return null; // all file nodes have been looked up, in vain
      }
      if (seeker != null && elementTypes.contains(seeker.getElementType())) {
        return seeker.getPsi();
      }
    }
    // here elt is null or a PsiFile is not up in the parent chain.
    return null;
  }

  @Nullable
  public static PsiElement getPrevNodeOf(PsiElement elt) {
    if (elt instanceof PsiFile) return null;  // no sense to get the previous node of a file
    return getPrevNodeOf(elt, PythonDialectsTokenSetProvider.INSTANCE.getNameDefinerTokens());
  }

  /**
   * Crawls up scopes of the PSI tree, checking named elements and name definers.
   */
  public static void scopeCrawlUp(@NotNull PsiScopeProcessor processor, @NotNull PsiElement element, @Nullable String name,
                                  @Nullable PsiElement roof) {
    // Use real context here to enable correct completion and resolve in case of PyExpressionCodeFragment!!!
    final PsiElement realContext = PyPsiUtils.getRealContext(element);
    final ScopeOwner originalOwner = ScopeUtil.getScopeOwner(realContext);
    final PsiElement parent = element.getParent();
    final boolean isGlobalOrNonlocal = parent instanceof PyGlobalStatement || parent instanceof PyNonlocalStatement;
    ScopeOwner owner = originalOwner;
    if (isGlobalOrNonlocal) {
      final ScopeOwner outerScopeOwner = ScopeUtil.getScopeOwner(owner);
      if (outerScopeOwner != null) {
        owner = outerScopeOwner;
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
      if (!(scopeOwner instanceof PyClass) || scopeOwner == originalScopeOwner) {
        final Scope scope = ControlFlowCache.getScope(scopeOwner);
        boolean found = false;
        if (name != null) {
          final PsiElement resolved = scope.getNamedElement(name);
          if (resolved != null) {
            if (!processor.execute(resolved, ResolveState.initial())) {
              found = true;
            }
          }
        }
        else {
          for (PsiNamedElement element : scope.getNamedElements()) {
            if (!processor.execute(element, ResolveState.initial())) {
              found = true;
              break;
            }
          }
        }
        for (NameDefiner definer : scope.getImportedNameDefiners()) {
          if (!processor.execute(definer, ResolveState.initial())) {
            found = true;
            break;
          }
        }
        if (found) {
          return;
        }
      }
      if (scopeOwner == roof) {
        return;
      }
      scopeOwner = ScopeUtil.getScopeOwner(scopeOwner);
    }
  }

  /**
   * Crawls up the PSI tree, checking nodes as if crawling backwards through source lexemes.
   *
   * @param processor a visitor that says when the crawl is done and collects info.
   * @param elt       element from which we start (not checked by processor); if null, the search immediately returns null.
   * @return first element that the processor accepted.
   */
  @Nullable
  public static PsiElement treeCrawlUp(PsiScopeProcessor processor, PsiElement elt) {
    if (elt == null || !elt.isValid()) return null; // can't find anyway.
    PsiElement seeker = elt;
    PsiElement cap = PyUtil.getConcealingParent(elt);
    PyFunction capFunction = cap != null ? PsiTreeUtil.getParentOfType(cap, PyFunction.class, false) : null;
    final boolean is_outside_param_list = PsiTreeUtil.getParentOfType(elt, PyParameterList.class) == null;
    do {
      ProgressManager.checkCanceled();
      seeker = getPrevNodeOf(seeker);
      // aren't we in the same defining assignment, global, etc?
      if ((seeker instanceof NameDefiner) && ((NameDefiner)seeker).mustResolveOutside() && PsiTreeUtil.isAncestor(seeker, elt, true)) {
        seeker = getPrevNodeOf(seeker);
      }
      // maybe we're under a cap?
      while (true) {
        PsiElement local_cap = PyUtil.getConcealingParent(seeker);
        if (local_cap == null) break; // seeker is in global context
        if (local_cap == cap) break; // seeker is in the same context as elt
        if ((cap != null) && PsiTreeUtil.isAncestor(local_cap, cap, true)) break; // seeker is in a context above elt's
        if (
          (local_cap != elt) && // elt isn't the cap of seeker itself
          ((cap == null) || !PsiTreeUtil.isAncestor(local_cap, cap, true)) // elt's cap is not under local cap
          ) { // only look at local cap and above
          if (local_cap instanceof NameDefiner) {
            seeker = local_cap;
          }
          else {
            seeker = getPrevNodeOf(local_cap);
          }
        }
        else {
          break;
        } // seeker is contextually under elt already
      }
      // maybe we're capped by a class? param lists are not capped though syntactically inside the function.
      if (is_outside_param_list && refersFromMethodToClass(capFunction, seeker)) continue;
      // names defined in a comprehension element are only visible inside it or the list comp expressions directly above it
      if (seeker instanceof PyComprehensionElement && !PsiTreeUtil.isAncestor(seeker, elt, false)) {
        continue;
      }
      // check what we got
      if (seeker != null) {
        if (!processor.execute(seeker, ResolveState.initial())) {
          if (processor instanceof ResolveProcessor) {
            return ((ResolveProcessor)processor).getResult();
          }
          else {
            return seeker;
          } // can't point to exact element, but somewhere here
        }
      }
    }
    while (seeker != null);
    if (processor instanceof ResolveProcessor) {
      return ((ResolveProcessor)processor).getResult();
    }
    return null;
  }

  /**
   * @param innerFunction a method, presumably inside the class
   * @param outer an element presumably in the class context.
   * @return true if an outer element is in a class context, while the inner is a method or function inside it.
   * @see com.jetbrains.python.psi.PyUtil#getConcealingParent(com.intellij.psi.PsiElement)
   */
  protected static boolean refersFromMethodToClass(final PyFunction innerFunction, final PsiElement outer) {
    if (innerFunction == null) {
      return false;
    }
    PsiElement outerClass = PyUtil.getConcealingParent(outer);
    if (outerClass instanceof PyClass &&   // outer is in a class context
       innerFunction.getContainingClass() == outerClass) {   // inner is a function or method within the class
      return true;
    }
    return false;
  }

  /**
   * Unwinds a multi-level qualified expression into a path, as seen in source text, i.e. outermost qualifier first.
   *
   * @param expr an expression to unwind.
   * @return path as a list of ref expressions.
   */
  @NotNull
  public static List<PyExpression> unwindQualifiers(@NotNull final PyQualifiedExpression expr) {
    final List<PyExpression> path = new LinkedList<PyExpression>();
    PyQualifiedExpression e = expr;
    while (e != null) {
      path.add(0, e);
      final PyExpression q = e.getQualifier();
      e = q instanceof PyQualifiedExpression ? (PyQualifiedExpression)q : null;
    }
    return path;
  }

  public static List<String> unwindQualifiersAsStrList(final PyQualifiedExpression expr) {
    final List<String> path = new LinkedList<String>();
    PyQualifiedExpression e = expr;
    while (e != null) {
      path.add(0, e.getText());
      final PyExpression q = e.getQualifier();
      e = q instanceof PyQualifiedExpression ? (PyQualifiedExpression)q : null;
    }
    return path;
  }

  public static String toPath(PyQualifiedExpression expr) {
    if (expr == null) return "";
    List<PyExpression> path = unwindQualifiers(expr);
    final QualifiedName qName = PyQualifiedNameFactory.fromReferenceChain(path);
    if (qName != null) {
      return qName.toString();
    }
    String name = expr.getName();
    if (name != null) {
      return name;
    }
    return "";
  }

  /**
   * Accepts only targets that are not the given object.
   */
  public static class FilterNotInstance implements Condition<PsiElement> {
    Object instance;

    public FilterNotInstance(Object instance) {
      this.instance = instance;
    }

    public boolean value(final PsiElement target) {
      return (instance != target);
    }

  }
}
