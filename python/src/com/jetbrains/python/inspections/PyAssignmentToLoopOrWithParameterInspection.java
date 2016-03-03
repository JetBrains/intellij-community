/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.google.common.collect.Lists;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

//TODO: Try to share logic with AssignmentToForLoopParameterInspection

/**
 * Checks for cases when you rewrite loop variable with inner loop.
 * It finds all <code>with</code> and <code>for</code> statements, takes variables declared by them and ensures none of parent
 * <code>with</code> or <code>for</code> declares variable with the same name
 *
 * @author link
 */
public class PyAssignmentToLoopOrWithParameterInspection extends PyInspection {

  private static final String NAME = PyBundle.message("INSP.NAME.assignment.to.loop.or.with.parameter.display.name");

  @NotNull
  @Override
  public String getDisplayName() {
    return NAME;
  }


  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    private Visitor(@Nullable final ProblemsHolder holder, @NotNull final LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyWithStatement(final PyWithStatement node) {
      checkNotReDeclaringUpperLoopOrStatement(node);
    }

    @Override
    public void visitPyForStatement(final PyForStatement node) {
      checkNotReDeclaringUpperLoopOrStatement(node);
    }

    /**
     * Finds first parent of specific type (See {@link #isRequiredStatement(com.intellij.psi.PsiElement)})
     * that declares one of names, declared in this statement
     */
    private void checkNotReDeclaringUpperLoopOrStatement(@NotNull final PsiElement statement) {
      for (final PsiElement declaredVar : getNamedElementsOfForAndWithStatements(statement)) {
        final Filter filter = new Filter(handleSubscriptionsAndResolveSafely(declaredVar));
        final PsiElement firstParent = PsiTreeUtil.findFirstParent(statement, true, filter);
        if ((firstParent != null) && isRequiredStatement(firstParent)) {
          // If parent is "for", we need to check that statement not declared in "else": PY-12367
          if ((firstParent instanceof PyForStatement) && isDeclaredInElse(statement, (PyForStatement)firstParent)) {
            continue;
          }
          registerProblem(declaredVar,
                          PyBundle.message("INSP.NAME.assignment.to.loop.or.with.parameter.display.message", declaredVar.getText()));
        }
      }
    }
  }

  /**
   * Checks that element is declared in "else" statement of "for" statement
   *
   * @param elementToCheck element to check
   * @param forStatement   statement to obtain "else" part from
   * @return true if declated in "Else" block
   */
  private static boolean isDeclaredInElse(@NotNull final PsiElement elementToCheck, @NotNull final PyForStatement forStatement) {
    final PyElsePart elsePart = forStatement.getElsePart();
    if (elsePart != null) {
      if (PsiTreeUtil.isAncestor(elsePart, elementToCheck, false)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Filters list of parents trying to find parent that declares var that refers to {@link #myNode}
   * Returns {@link com.jetbrains.python.codeInsight.controlflow.ScopeOwner} if nothing found.
   * Returns parent otherwise.
   */
  private static class Filter implements Condition<PsiElement> {
    private final PsiElement myNode;

    private Filter(final PsiElement node) {
      this.myNode = node;
    }

    @Override
    public boolean value(final PsiElement psiElement) {
      if (psiElement instanceof ScopeOwner) {
        return true; //Do not go any further
      }
      if (!isRequiredStatement(psiElement)) {
        return false; //Parent has wrong type, skip
      }
      final List<PyElement> varsDeclaredInStatement = getNamedElementsOfForAndWithStatements(psiElement);
      for (final PsiElement varDeclaredInStatement : varsDeclaredInStatement) {
        //For each variable, declared by this parent take first declaration and open subscription list if any
        final PsiReference reference = handleSubscriptionsAndResolveSafely(varDeclaredInStatement).getReference();
        if ((reference != null) && reference.isReferenceTo(myNode)) {
          return true; //One of variables declared by this parent refers to node
        }
      }
      return false;
    }
  }

  /**
   * Opens subscription list (<code>i[n][q][f] --&gt; i</code>) and resolves ref recursively to the topmost element,
   * but not further than file borders (to prevent Stub to AST conversion)
   *
   * @param element element to open and resolve
   * @return opened and resolved element
   */
  private static PsiElement handleSubscriptionsAndResolveSafely(PsiElement element) {
    assert element != null;
    if (element instanceof PySubscriptionExpression) {
      element = ((PySubscriptionExpression)element).getRootOperand();
    }
    element = PyUtil.resolveToTheTop(element);
    return element;
  }

  /**
   * Checks if element is statement this inspection should work with
   *
   * @param element to check
   * @return true if inspection should work with this element
   */
  private static boolean isRequiredStatement(final PsiElement element) {
    assert element != null;
    return (element instanceof PyWithStatement) || (element instanceof PyForStatement);
  }

  private static List<PyElement> getNamedElementsOfForAndWithStatements(@NotNull PsiElement element) {
    final List<PyElement> expressions;
    if (element instanceof PyForStatement) {
      final PyForStatement forStmt = (PyForStatement)element;
      final PyExpression tgt = forStmt.getForPart().getTarget();
      expressions = Lists.newArrayList();
      expressions.addAll(PyUtil.flattenedParensAndStars(tgt));
    }
    else if (element instanceof PyWithStatement) {
      final PyWithStatement withStmt = (PyWithStatement)element;
      expressions = Lists.newArrayList();
      final PyWithItem[] items = PsiTreeUtil.getChildrenOfType(withStmt, PyWithItem.class);
      if (items != null) {
        for (PyWithItem item : items) {
          PyExpression targetExpression = item.getTarget();
          expressions.addAll(PyUtil.flattenedParensAndTuples(targetExpression));
        }
      }
    }
    else {
      expressions = Collections.emptyList();
    }
    return expressions;
  }
}
