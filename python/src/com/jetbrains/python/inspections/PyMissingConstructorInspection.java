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
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyClass(final PyClass node) {
      PsiElement[] superClasses = node.getSuperClassExpressions();
      if (superClasses.length == 0 || (superClasses.length == 1 && OBJECT.equals(superClasses[0].getText())))
        return;

      if (!superHasConstructor(node)) return;
      PyFunction initMethod = node.findMethodByName(INIT, false, null);
      if (initMethod != null) {
        if (isExceptionClass(node, myTypeEvalContext) || hasConstructorCall(node, initMethod)) {
          return;
        }
        if (superClasses.length == 1 || node.isNewStyleClass(null))
          registerProblem(initMethod.getNameIdentifier(), PyBundle.message("INSP.missing.super.constructor.message"),
                          new AddCallSuperQuickFix());
        else
          registerProblem(initMethod.getNameIdentifier(), PyBundle.message("INSP.missing.super.constructor.message"));
      }
    }

    private boolean superHasConstructor(@NotNull PyClass cls) {
      final String className = cls.getName();
      for (PyClass c : cls.getAncestorClasses(myTypeEvalContext)) {
        final String name = c.getName();
        if (!PyUtil.isObjectClass(c) && !Comparing.equal(className, name) && c.findMethodByName(INIT, false, null) != null) {
          return true;
        }
      }
      return false;
    }

    private boolean isExceptionClass(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
      if (PyBroadExceptionInspection.equalsException(cls, context)) {
        return true;
      }
      for (PyClass baseClass : cls.getAncestorClasses(myTypeEvalContext)) {
        if (PyBroadExceptionInspection.equalsException(baseClass, context)) {
          return true;
        }
      }
      return false;
    }

    private boolean hasConstructorCall(PyClass node, PyFunction initMethod) {
      PyStatementList statementList = initMethod.getStatementList();
      CallVisitor visitor = new CallVisitor(node);
      if (statementList != null) {
        statementList.accept(visitor);
        return visitor.myHasConstructorCall;
      }
      return false;
    }

    private class CallVisitor extends PyRecursiveElementVisitor {
      private boolean myHasConstructorCall = false;
      private PyClass myClass;
      CallVisitor(PyClass node) {
        myClass = node;
      }

      @Override
      public void visitPyCallExpression(PyCallExpression node) {
        if (isConstructorCall(node, myClass))
          myHasConstructorCall = true;
      }

      private boolean isConstructorCall(PyCallExpression expression, PyClass cl) {
        PyExpression callee = expression.getCallee();
        if (callee instanceof PyQualifiedExpression) {
          PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
          if (qualifier != null) {
            String tmp = "";
            if (qualifier instanceof PyCallExpression) {
              PyExpression innerCallee = ((PyCallExpression)qualifier).getCallee();
              if (innerCallee != null) {
                tmp = innerCallee.getName();
              }
              if (SUPER.equals(tmp) && (INIT.equals(callee.getName()))) {
                PyExpression[] args = ((PyCallExpression)qualifier).getArguments();
                if (args.length > 0) {
                  String firstArg = args[0].getText();
                  final String qualifiedName = cl.getQualifiedName();
                  if (firstArg.equals(cl.getName()) || firstArg.equals(CANONICAL_SELF+"."+ __CLASS__) ||
                      (qualifiedName != null && qualifiedName.endsWith(firstArg)))
                      return true;
                  for (PyClass s : cl.getAncestorClasses(myTypeEvalContext)) {
                    if (firstArg.equals(s.getName()))
                      return true;
                  }
                }
                else
                  return true;
              }
            }
            if (INIT.equals(callee.getName())) {
              return isSuperClassCall(cl, qualifier);
            }
          }
        }
        return false;
      }

      private boolean isSuperClassCall(PyClass cl, PyExpression qualifier) {
        PsiElement callingClass = null;
        if (qualifier instanceof PyCallExpression) {
          PyExpression innerCallee = ((PyCallExpression)qualifier).getCallee();
          if (innerCallee != null) {
            PsiReference ref = innerCallee.getReference();
            if (ref != null)
              callingClass = ref.resolve();
          }
        }
        else {
          PsiReference ref = qualifier.getReference();
          if (ref != null)
            callingClass = ref.resolve();
        }
        for (PyClass s : cl.getAncestorClasses(myTypeEvalContext)) {
          if (s.equals(callingClass)) {
            return true;
          }
        }
        return false;
      }
    }
  }
}
