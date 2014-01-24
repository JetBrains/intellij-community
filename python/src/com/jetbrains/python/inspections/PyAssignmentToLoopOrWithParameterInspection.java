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
                                        boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    private Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyWithStatement(PyWithStatement node) {
      checkNotReDeclaringUpperLoopOrStatement(node);
    }

    @Override
    public void visitPyForStatement(PyForStatement node) {
      checkNotReDeclaringUpperLoopOrStatement(node);
    }

    /**
     * Finds first parent of specific type (See {@link #isRequiredStatement(com.intellij.psi.PsiElement)})
     * that declares one of names, declared in this statement
     */
    private void checkNotReDeclaringUpperLoopOrStatement(NameDefiner statement) {
      for (PsiElement declaredVar : statement.iterateNames()) {
        Filter filter = new Filter(handleSubscriptionsAndResolveSafely(declaredVar));
        PsiElement firstParent = PsiTreeUtil.findFirstParent(statement, true, filter);
        if (firstParent != null && isRequiredStatement(firstParent)) {
          registerProblem(declaredVar,
                          PyBundle.message("INSP.NAME.assignment.to.loop.or.with.parameter.display.message", declaredVar.getText()));
        }
      }
    }
  }

  /**
   * Filters list of parents trying to find parent that declares var that refers to {@link #node}
   * Returns {@link com.jetbrains.python.codeInsight.controlflow.ScopeOwner} if nothing found.
   * Returns parent otherwise.
   */
  private static class Filter implements Condition<PsiElement> {
    private final PsiElement node;

    private Filter(PsiElement node) {
      this.node = node;
    }

    @Override
    public boolean value(PsiElement psiElement) {
      if (psiElement instanceof ScopeOwner) {
        return true; //Do not go any further
      }
      if (!(isRequiredStatement(psiElement))) {
        return false; //Parent has wrong type, skip
      }
      Iterable<PyElement> varsDeclaredInStatement = ((NameDefiner)psiElement).iterateNames();
      for (PsiElement varDeclaredInStatement : varsDeclaredInStatement) {
        //For each variable, declared by this parent take first declaration and open subscription list if any
        PsiReference reference = handleSubscriptionsAndResolveSafely(varDeclaredInStatement).getReference();
        if (reference != null && reference.isReferenceTo(node)) {
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
    while (true) {
      PsiReference reference = element.getReference();
      if (reference == null) {
        break;
      }
      PsiElement resolve = reference.resolve();
      if (resolve == null || resolve.equals(element) || !PyUtil.inSameFile(resolve, element)) {
        break;
      }
      element = resolve;
    }
    return element;
  }

  /**
   * Checks if element is statement this inspection should work with
   *
   * @param element to check
   * @return true if inspection should work with this element
   */
  private static boolean isRequiredStatement(PsiElement element) {
    assert element != null;
    return element instanceof PyWithStatement || element instanceof PyForStatement;
  }
}
