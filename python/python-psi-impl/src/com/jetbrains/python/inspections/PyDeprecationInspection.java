/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyDeprecatable;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiFile;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;


public final class PyDeprecationInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 final boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    TypeEvalContext context = PyInspectionVisitor.getContext(session);
    if (context.getUsesExternalTypeEngine()) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new Visitor(holder, context);
  }

  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder,
            @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    private final Set<PsiElement> myReportedDeprecations = new HashSet<>();

    @Override
    public void visitPyBinaryExpression(@NotNull PyBinaryExpression node) {
      final PsiElement resolveResult = node.getReference(getResolveContext()).resolve();
      if (resolveResult instanceof PyDeprecatable deprecatable) {
        @NlsSafe String deprecationMessage = (deprecatable.getDeprecationMessage());
        if (deprecationMessage != null) {
          registerProblem(node.getPsiOperator(), deprecationMessage, ProblemHighlightType.WARNING);
        }
      }
    }

    @Override
    public void visitPyAugAssignmentStatement(@NotNull PyAugAssignmentStatement node) {
      final PsiElement resolveResult = node.getReference(getResolveContext()).resolve();
      if (resolveResult instanceof PyDeprecatable deprecatable) {
        @NlsSafe String deprecationMessage = (deprecatable.getDeprecationMessage());
        if (deprecationMessage != null) {
          registerProblem(node.getOperation(), deprecationMessage, ProblemHighlightType.WARNING);
        }
      }
    }

    @Override
    public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
      if (isInsideImportErrorGuard(node)) return;

      final PsiElement resolveResult = resolve(node);
      if (isFromImportOfDifferentSource(node, resolveResult)) return;

      if (node.getParent() instanceof PyQualifiedExpression qualifiedExpression) {
        AccessDirection accessDirection =  qualifiedExpression.getParent() instanceof PyAugAssignmentStatement
                                           ? AccessDirection.WRITE
                                           : AccessDirection.of(qualifiedExpression);
        if (reportDeprecatedProperty(qualifiedExpression, accessDirection)) return;
      }

      if (handleCallSiteDeprecation(node, resolveResult)) return;

      handleSymbolDeprecation(node, resolveResult);
    }

    private static boolean isInsideImportErrorGuard(@NotNull PyReferenceExpression node) {
      final PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(node, PyExceptPart.class);
      if (exceptPart == null) return false;
      final PyExpression exceptClass = exceptPart.getExceptClass();
      return exceptClass != null && "ImportError".equals(exceptClass.getText());
    }

    private static boolean isFromImportOfDifferentSource(@NotNull PyReferenceExpression node,
                                                         @Nullable PsiElement resolveResult) {
      final PyFromImportStatement importStatement = PsiTreeUtil.getParentOfType(node, PyFromImportStatement.class);
      if (importStatement == null) return false;
      final PsiElement importSource = importStatement.resolveImportSource();
      return resolveResult != null && importSource != resolveResult.getContainingFile();
    }

    private boolean handleCallSiteDeprecation(@NotNull PyReferenceExpression node,
                                              @Nullable PsiElement resolveResult) {
      if (!(node.getParent() instanceof PyCallExpression call) || call.getCallee() != node) {
        return false;
      }

      if (resolveResult instanceof PyClass cls) {
        reportDeprecatedAttribute(cls, node);
      }

      PyElement target = resolveCallee(call, resolveResult);
      if (!(target instanceof PyDeprecatable deprecatable)) return false;

      String deprecationMessage = deprecatable.getDeprecationMessage();
      if (deprecationMessage != null) {
        reportDeprecatedAttribute(deprecatable, node);
      }
      else if (!(target.getContainingFile() instanceof PyiFile)) {
        PyElement matchingStubElement = findMatchingStubElement(target, call);
        if (matchingStubElement instanceof PyDeprecatable stubDeprecatable) {
          reportDeprecatedAttribute(stubDeprecatable, node);
        }
      }
      return true;
    }

    private void handleSymbolDeprecation(@NotNull PyReferenceExpression node,
                                         @Nullable PsiElement resolveResult) {
      @NlsSafe String deprecationMessage = null;
      if (resolveResult instanceof PyDeprecatable deprecatedSymbol) {
        deprecationMessage = deprecatedSymbol.getDeprecationMessage();

        if (deprecationMessage == null && !(resolveResult.getContainingFile() instanceof PyiFile)) {
          PsiElement stub = PyiUtil.getPythonStub((PyElement)deprecatedSymbol);
          if (stub instanceof PyDeprecatable stubDeprecatable) {
            deprecationMessage = stubDeprecatable.getDeprecationMessage();
          }
        }
      }
      else if (resolveResult instanceof PyFile) {
        deprecationMessage = ((PyFile)resolveResult).getDeprecationMessage();
      }
      if (deprecationMessage == null) return;

      ASTNode nameElement = node.getNameElement();
      PsiElement anchor = nameElement == null ? node : nameElement.getPsi();
      if (myReportedDeprecations.add(anchor)) {
        registerProblem(anchor, deprecationMessage, ProblemHighlightType.LIKE_DEPRECATED);
      }
    }

    private @Nullable PyElement findMatchingStubElement(@NotNull PyElement target,
                                                        @NotNull PyCallExpression call) {
      PsiElement stub = PyiUtil.getPythonStub(target);
      if (!(stub instanceof PyFunction stubFn)) return (PyElement) stub;

      TypeEvalContext ctx = getResolveContext().getTypeEvalContext();

      PyFunction matchingStubOverload = PyCallExpressionHelper.selectMatchingOverload(stubFn, call, ctx);
      return matchingStubOverload != null ? matchingStubOverload : stubFn;
    }

    private @Nullable PyElement resolve(@NotNull PyReferenceExpression node) {
      final PyElement resolve = PyUtil.as(node.getReference(getResolveContext()).resolve(), PyElement.class);
      return resolve == null ? null : PyiUtil.getOriginalElementOrLeaveAsIs(resolve, PyElement.class);
    }

    private PyElement resolveCallee(@NotNull PyCallExpression call,
                                    @Nullable PsiElement resolvedResult) {
      PyResolveContext resolveContext = getResolveContext();
      TypeEvalContext typeEvalContext = resolveContext.getTypeEvalContext();

      if (resolvedResult instanceof PyFunction function) {
        PyFunction matchingOverload = PyCallExpressionHelper.selectMatchingOverload(function, call, typeEvalContext);
        PyElement target = matchingOverload != null ? matchingOverload : function;
        return PyiUtil.getOriginalElementOrLeaveAsIs(target, PyElement.class);
      }

      PyFunction fallback = null;
      for (PyCallableType callableType : call.multiResolveCallee(resolveContext)) {
        PyElement callable = callableType.getCallable();
        if (callable instanceof PyFunction function) {
          PyElement matching = PyCallExpressionHelper.selectMatchingOverload(function, call, typeEvalContext);
          if (matching != null) {
            return PyiUtil.getOriginalElementOrLeaveAsIs(matching, PyElement.class);
          }
          fallback = function;
        }
      }
      return fallback != null ? PyiUtil.getOriginalElementOrLeaveAsIs(fallback, PyElement.class) : null;
    }

    private boolean reportDeprecatedProperty(@NotNull PyQualifiedExpression expr,
                                             @NotNull AccessDirection direction) {
      PyExpression qualifier = expr.getQualifier();
      String name = expr.getName();
      if (qualifier == null || name == null) return false;

      PyType type = myTypeEvalContext.getType(qualifier);
      if (!(type instanceof PyClassType classType)) return false;

      Property property = classType.getPyClass().findProperty(name, true, myTypeEvalContext);
      if (property == null) return false;

      PyCallable accessor = property.getByDirection(direction).valueOrNull();
      if (!(accessor instanceof PyDeprecatable deprecatable)) return false;

      return reportDeprecatedAttribute(deprecatable, expr);
    }

    private boolean reportDeprecatedAttribute(@NotNull PyDeprecatable deprecatable,
                                              @NotNull PyQualifiedExpression qualified) {
      @NlsSafe String message = deprecatable.getDeprecationMessage();
      if (message == null) return false;

      ASTNode nameElement = qualified.getNameElement();
      PsiElement highlight = nameElement != null ? nameElement.getPsi() : qualified;

      if (!myReportedDeprecations.add(highlight)) return false;

      registerProblem(highlight, message, ProblemHighlightType.LIKE_DEPRECATED);
      return true;
    }
  }
}
