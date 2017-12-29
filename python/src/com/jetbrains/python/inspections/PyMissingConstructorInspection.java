/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.AddCallSuperQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

import static com.jetbrains.python.PyNames.*;

/**
 * User: catherine
 *
 * Inspection to warn if call to super constructor in class is missed
 */
public class PyMissingConstructorInspection extends PyInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.missing.super.constructor");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyClass(@NotNull PyClass node) {
      final PsiElement[] superClasses = node.getSuperClassExpressions();

      if (superClasses.length == 0 ||
          superClasses.length == 1 && OBJECT.equals(superClasses[0].getText()) ||
          !superHasConstructor(node, myTypeEvalContext)) {
        return;
      }

      final PyFunction initMethod = node.findMethodByName(INIT, false, myTypeEvalContext);

      if (initMethod == null || isExceptionClass(node, myTypeEvalContext) || hasConstructorCall(node, initMethod, myTypeEvalContext)) {
        return;
      }

      if (superClasses.length == 1 || node.isNewStyleClass(myTypeEvalContext)) {
        registerProblem(initMethod.getNameIdentifier(), PyBundle.message("INSP.missing.super.constructor.message"),
                        new AddCallSuperQuickFix());
      }
      else {
        registerProblem(initMethod.getNameIdentifier(), PyBundle.message("INSP.missing.super.constructor.message"));
      }
    }

    private static boolean superHasConstructor(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
      final String className = cls.getName();

      for (PyClass baseClass : cls.getAncestorClasses(context)) {
        if (!PyUtil.isObjectClass(baseClass) &&
            !Comparing.equal(className, baseClass.getName()) &&
            baseClass.findMethodByName(INIT, false, context) != null) {
          return true;
        }
      }

      return false;
    }

    private static boolean isExceptionClass(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
      if (PyBroadExceptionInspection.equalsException(cls, context)) {
        return true;
      }

      return cls.getAncestorClasses(context)
        .stream()
        .anyMatch(baseClass -> PyBroadExceptionInspection.equalsException(baseClass, context));
    }

    private static boolean hasConstructorCall(@NotNull PyClass cls, @NotNull PyFunction initMethod, @NotNull TypeEvalContext context) {
      final CallVisitor visitor = new CallVisitor(cls, context);
      initMethod.getStatementList().accept(visitor);
      return visitor.myHasConstructorCall;
    }

    private static class CallVisitor extends PyRecursiveElementVisitor {

      @NotNull
      private final PyClass myClass;

      @NotNull
      private final TypeEvalContext myContext;

      private boolean myHasConstructorCall = false;

      public CallVisitor(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
        myClass = cls;
        myContext = context;
      }

      @Override
      public void visitPyCallExpression(@NotNull PyCallExpression node) {
        if (isConstructorCall(node, myClass, myContext)) {
          myHasConstructorCall = true;
        }
      }

      private static boolean isConstructorCall(@NotNull PyCallExpression call, @NotNull PyClass cls, @NotNull TypeEvalContext context) {
        final PyExpression callee = call.getCallee();

        if (callee == null || !INIT.equals(callee.getName())) {
          return false;
        }

        final PyExpression calleeQualifier = Optional
          .of(callee)
          .filter(PyQualifiedExpression.class::isInstance)
          .map(PyQualifiedExpression.class::cast)
          .map(PyQualifiedExpression::getQualifier)
          .orElse(null);

        return calleeQualifier != null && (isSuperCall(calleeQualifier, cls, context) || isSuperClassCall(calleeQualifier, cls, context));
      }

      private static boolean isSuperCall(@NotNull PyExpression calleeQualifier,
                                         @NotNull PyClass cls,
                                         @NotNull TypeEvalContext context) {
        final String prevCalleeName = Optional
          .of(calleeQualifier)
          .filter(PyCallExpression.class::isInstance)
          .map(PyCallExpression.class::cast)
          .map(PyCallExpression::getCallee)
          .map(PyExpression::getName)
          .orElse(null);

        if (!SUPER.equals(prevCalleeName)) {
          return false;
        }

        final PyExpression[] args = ((PyCallExpression)calleeQualifier).getArguments();

        if (args.length == 0) {
          return true;
        }

        final String firstArg = args[0].getText();
        final String classQName = cls.getQualifiedName();

        if (firstArg.equals(cls.getName()) ||
            firstArg.equals(CANONICAL_SELF + "." + __CLASS__) ||
            classQName != null && classQName.endsWith(firstArg) ||
            firstArg.equals(__CLASS__) && LanguageLevel.forElement(cls).isAtLeast(LanguageLevel.PYTHON30)) {
          return true;
        }

        return cls.getAncestorClasses(context)
          .stream()
          .map(PyClass::getName)
          .anyMatch(firstArg::equals);
      }

      private static boolean isSuperClassCall(@NotNull PyExpression calleeQualifier,
                                              @NotNull PyClass cls,
                                              @NotNull TypeEvalContext context) {
        final PsiElement callingClass = resolveCallingClass(calleeQualifier);

        return callingClass != null &&
               cls.getAncestorClasses(context)
                 .stream()
                 .anyMatch(callingClass::equals);
      }

      @Nullable
      private static PsiElement resolveCallingClass(@NotNull PyExpression calleeQualifier) {
        if (calleeQualifier instanceof PyCallExpression) {
          return Optional
            .of((PyCallExpression)calleeQualifier)
            .map(PyCallExpression::getCallee)
            .map(PyExpression::getReference)
            .map(PsiReference::resolve)
            .orElse(null);
        }
        else {
          return Optional
            .ofNullable(calleeQualifier.getReference())
            .map(PsiReference::resolve)
            .orElse(null);
        }
      }
    }
  }
}
