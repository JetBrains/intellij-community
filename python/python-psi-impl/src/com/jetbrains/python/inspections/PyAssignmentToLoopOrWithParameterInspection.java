// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//TODO: Try to share logic with AssignmentToForLoopParameterInspection

/**
 * Checks for cases when you rewrite loop variable with inner loop.
 * It finds all {@code with} and {@code for} statements, takes variables declared by them and ensures none of parent
 * {@code with} or {@code for} declares variable with the same name
 *
 * @author link
 */
public final class PyAssignmentToLoopOrWithParameterInspection extends PyInspection {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static final class Visitor extends PyInspectionVisitor {
    private Visitor(@Nullable final ProblemsHolder holder,
                    @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyWithStatement(final @NotNull PyWithStatement node) {
      checkNotReDeclaringUpperLoopOrStatement(node);
    }

    @Override
    public void visitPyForStatement(final @NotNull PyForStatement node) {
      checkNotReDeclaringUpperLoopOrStatement(node);
    }

    /**
     * Finds first parent of specific type (See {@link #isRequiredStatement(PsiElement)})
     * that declares one of names, declared in this statement
     */
    private void checkNotReDeclaringUpperLoopOrStatement(@NotNull final PsiElement statement) {
      for (PyExpression declaredVar : getNamedElementsOfForAndWithStatements(statement)) {
        final Filter filter = new Filter(handleSubscriptionsAndResolveSafely(declaredVar));
        final PsiElement firstParent = PsiTreeUtil.findFirstParent(statement, true, filter);
        if ((firstParent != null) && isRequiredStatement(firstParent)) {
          // If parent is "for", we need to check that statement not declared in "else": PY-12367
          if ((firstParent instanceof PyForStatement) && isDeclaredInElse(statement, (PyForStatement)firstParent)) {
            continue;
          }
          registerProblem(declaredVar,
                          PyPsiBundle.message("INSP.assignment.to.loop.or.with.parameter", declaredVar.getText()));
        }
      }
    }
  }

  /**
   * Checks that element is declared in "else" statement of "for" statement
   *
   * @param elementToCheck element to check
   * @param forStatement   statement to obtain "else" part from
   * @return true if declared in "Else" block
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
   * Returns {@link ScopeOwner} if nothing found.
   * Returns parent otherwise.
   */
  private static final class Filter implements Condition<PsiElement> {
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
      final List<PyExpression> varsDeclaredInStatement = getNamedElementsOfForAndWithStatements(psiElement);
      for (PyExpression varDeclaredInStatement : varsDeclaredInStatement) {
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
   * Opens subscription list ({@code i[n][q][f] --> i}) and resolves ref recursively to the topmost element,
   * but not further than file borders (to prevent Stub to AST conversion)
   *
   * @param element element to open and resolve
   * @return opened and resolved element
   */
  @NotNull
  private static PsiElement handleSubscriptionsAndResolveSafely(@NotNull PyExpression element) {
    if (element instanceof PySubscriptionExpression) {
      element = ((PySubscriptionExpression)element).getRootOperand();
    }
    return PyUtil.resolveToTheTop(element);
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

  @NotNull
  private static List<PyExpression> getNamedElementsOfForAndWithStatements(@NotNull PsiElement element) {
    if (element instanceof PyForStatement forStatement) {
      final PyExpression target = forStatement.getForPart().getTarget();

      return dropUnderscores(PyUtil.flattenedParensAndStars(target));
    }
    else if (element instanceof PyWithStatement withStatement) {
      final List<PyExpression> result = new ArrayList<>();

      for (PyWithItem item : withStatement.getWithItems()) {
        final PyExpression target = item.getTarget();
        if (target != null) {
          result.addAll(PyUtil.flattenedParensAndTuples(target));
        }
      }

      return dropUnderscores(result);
    }

    return Collections.emptyList();
  }

  @NotNull
  private static List<PyExpression> dropUnderscores(@NotNull List<PyExpression> expressions) {
    return ContainerUtil.filter(expressions,
                                expression -> !PyNames.UNDERSCORE.equals(expression.getText()));
  }
}
