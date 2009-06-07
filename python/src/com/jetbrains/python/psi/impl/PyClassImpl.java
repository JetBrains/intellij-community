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

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDosStringFinder;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.VariantsProcessor;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 03.06.2005
 * Time: 0:27:33
 * To change this template use File | Settings | File Templates.
 */
public class PyClassImpl extends PyPresentableElementImpl<PyClassStub> implements PyClass {

  public static final PyClass[] EMPTY_ARRAY = new PyClassImpl[0];

  public PyClassImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyClassImpl(final PyClassStub stub) {
    super(stub, PyElementTypes.CLASS_DECLARATION);
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final ASTNode nameElement = getLanguage().getElementGenerator().createNameIdentifier(getProject(), name);
    getNode().replaceChild(findNameIdentifier(), nameElement);
    return this;
  }

  @Nullable
  @Override
  public String getName() {
    final PyClassStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    else {
      ASTNode node = findNameIdentifier();
      return node != null ? node.getText() : null;
    }
  }

  private ASTNode findNameIdentifier() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Override
  public Icon getIcon(int flags) {
    return Icons.CLASS_ICON;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyClass(this);
  }

  @NotNull
  public PyStatementList getStatementList() {
    return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
  }

  @NotNull
  public PyExpression[] getSuperClassExpressions() {
    final PyParenthesizedExpression superExpression = PsiTreeUtil.getChildOfType(this, PyParenthesizedExpression.class);
    if (superExpression != null) {
      PyExpression expr = superExpression.getContainedExpression();
      if (expr instanceof PyTupleExpression) {
        return ((PyTupleExpression) expr).getElements();
      }
      if (expr != null) {
        return new PyExpression[] { expr };
      }
    }
    return PyExpression.EMPTY_ARRAY;
  }

  public PsiElement[] getSuperClassElements() {
    final PyExpression[] superExpressions = getSuperClassExpressions();
    List<PsiElement> superClasses = new ArrayList<PsiElement>();
    for(PyExpression expr: superExpressions) {
      if (expr instanceof PyReferenceExpression) {
        PyReferenceExpression ref = (PyReferenceExpression) expr;
        final PsiElement result = ref.resolve();
        if (result != null) {
          superClasses.add(result);
        }
      }
    }
    return superClasses.toArray(new PsiElement[superClasses.size()]);
  }

  /* The implementation is manifestly lazy wrt psi scanning and uses stack rather sparingly.
   It must be more efficient on deep and wide hierarchies, but it was more fun than efficiency that produced it.
   */
  public Iterable<PyClass> iterateAncestors() {
    return new Iterable<PyClass>() {
      public Iterator<PyClass> iterator() {
        return new Iterator<PyClass>() {
          List<PyClass> pending = new LinkedList<PyClass>();
          Set<PyClass> seen = new HashSet<PyClass>();
          Iterator<PyClass> percolator = getSuperClassesList().iterator();
          PyClass prefetch = null;

          public boolean hasNext() {
            // due to already-seen filtering, there's no way but to try and see.
            if (prefetch != null) return true;
            try {
              prefetch = next();
              return true;
            }
            catch (NoSuchElementException e) {
              return false;
            }
          }

          public PyClass next() {
            if (prefetch != null) {
              PyClass ret = prefetch;
              prefetch = null;
              return ret;
            }
            if (percolator.hasNext()) {
              PyClass it = percolator.next();
              if (seen.contains(it)) return next();
              pending.add(it);
              seen.add(it);
              return it;
            }
            else if (pending.size() > 0) {
              PyClass it = pending.get(0);
              pending.remove(0); // t, ts* = pending
              percolator = it.iterateAncestors().iterator();
              return next();
            }
            else throw new NoSuchElementException();
          }

          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  protected List<PyClass> getSuperClassesList() {
    PsiElement[] superClassElements = getSuperClassElements();
    if (superClassElements != null) {
      List<PyClass> result = new ArrayList<PyClass>();
      // maybe a bare old-style class?
      // TODO: depend on language version: py3k does not do old style classes
      PsiElement paren = PsiTreeUtil.getChildOfType(this, PyParenthesizedExpression.class).getFirstChild(); // no NPE, we always have the par expr
      if (paren != null && "(".equals(paren.getText())) { // "()" after class name, it's new style
        for(PsiElement element: superClassElements) {
          if (element instanceof PyClass) {
            result.add((PyClass) element);
          }
        }
      }
      else if (! PyBuiltinCache.BUILTIN_FILE.equals(getContainingFile().getName())) { // old-style *and* not builtin object() 
        PyClass oldstyler = PyBuiltinCache.getInstance(getProject()).getClass("___Classobj");
        if (oldstyler != null) result.add(oldstyler);
      }
      return result;
    }
    return new ArrayList<PyClass>(0); 
  }

  @NotNull
  public PyClass[] getSuperClasses() {
    PsiElement[] superClassElements = getSuperClassElements();
    if (superClassElements != null) {
      List<PyClass> result = new ArrayList<PyClass>();
      for(PsiElement element: superClassElements) {
        if (element instanceof PyClass) {
          result.add((PyClass) element);
        }
      }
      return result.toArray(new PyClass[result.size()]);
    }
    return EMPTY_ARRAY;
  }

  @NotNull
  public PyFunction[] getMethods() {
    // TODO: gather all top-level functions, maybe within control statements
    final PyClassStub classStub = getStub();
    if (classStub != null) {
      return classStub.getChildrenByType(PyElementTypes.FUNCTION_DECLARATION, PyFunction.EMPTY_ARRAY);
    }
    List<PyFunction> result = new ArrayList<PyFunction>();
    final PyStatementList statementList = getStatementList();
    for (PsiElement element : statementList.getChildren()) {
      if (element instanceof PyFunction) {
        result.add((PyFunction) element);
      }
    }
    return result.toArray(new PyFunction[result.size()]);
  }

  public PyFunction findMethodByName(@NotNull final String name) {
    PyFunction[] methods = getMethods();
    for(PyFunction method: methods) {
      if (name.equals(method.getName())) {
        return method;
      }
    }
    return null;
  }

  public PyTargetExpression[] getClassAttributes() {
    PyClassStub stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(PyElementTypes.TARGET_EXPRESSION, PyTargetExpression.EMPTY_ARRAY);
    }
    List<PyTargetExpression> result = new ArrayList<PyTargetExpression>();
    for (PsiElement psiElement : getStatementList().getChildren()) {
      if (psiElement instanceof PyAssignmentStatement) {
        final PyAssignmentStatement assignmentStatement = (PyAssignmentStatement)psiElement;
        final PyExpression[] targets = assignmentStatement.getTargets();
        for (PyExpression target : targets) {
          if (target instanceof PyTargetExpression) {
            result.add((PyTargetExpression) target);
          }
        }
      }
    }
    return result.toArray(new PyTargetExpression[result.size()]);
  }

  public PyTargetExpression[] getInstanceAttributes() {
    PyFunctionImpl initMethod = (PyFunctionImpl) findMethodByName(PyNames.INIT);
    if (initMethod == null) return PyTargetExpression.EMPTY_ARRAY;
    final PyParameter[] params = initMethod.getParameterList().getParameters();
    if (params.length == 0) return PyTargetExpression.EMPTY_ARRAY;

    final PyFunctionStub methodStub = initMethod.getStub();
    if (methodStub != null) {
      return methodStub.getChildrenByType(PyElementTypes.TARGET_EXPRESSION, PyTargetExpression.EMPTY_ARRAY);
    }

    final List<PyTargetExpression> result = new ArrayList<PyTargetExpression>();
    // NOTE: maybe treeCrawlUp would be more precise, but currently it works well enough to not care. 
    initMethod.getStatementList().accept(new PyRecursiveElementVisitor() {
      public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
        super.visitPyAssignmentStatement(node);
        final PyExpression[] targets = node.getTargets();
        for(PyExpression target: targets) {
          if (target instanceof PyTargetExpression) {
            PyExpression qualifier = ((PyTargetExpression) target).getQualifier();
            if (qualifier != null && qualifier.getText().equals(params [0].getName())) {
              result.add((PyTargetExpression)target);
            }
          }
        }
      }
    });
    return result.toArray(new PyTargetExpression[result.size()]);
  }

  public boolean isNewStyleClass() {
    PyClass objclass = PyBuiltinCache.getInstance(getProject()).getClass("object");
    if (this == objclass) return true; // a rare but possible case
    for (PyClass ancestor : iterateAncestors()) {
      if (ancestor == objclass) return true;
    }
    return false;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place)
  {
    // class level
    final PsiElement the_psi = getNode().getPsi();
    PyResolveUtil.treeCrawlUp(processor, true, the_psi, the_psi);
    // instance level
    for(PyTargetExpression expr: getInstanceAttributes()) {
      if (expr == lastParent) continue;
      if (!processor.execute(expr, substitutor)) return false;
    }
    //
    if (processor instanceof VariantsProcessor) {
      return true;
    }
    return processor.execute(this, substitutor);
  }

  public int getTextOffset() {
    final ASTNode name = findNameIdentifier();
    return name != null ? name.getStartOffset() : super.getTextOffset();
  }

  public PyStringLiteralExpression getDocStringExpression() {
    return PythonDosStringFinder.find(getStatementList());
  }

  public String toString() {
    return "PyClass: " + getName();
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    return new SingleIterable<PyElement>(this);
  }

  public PyElement getElementNamed(final String the_name) {
    return the_name.equals(getName())? this: null;
  }

  public boolean mustResolveOutside() {
    return false;
  }

}
