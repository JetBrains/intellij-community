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
import com.intellij.psi.ResolveResult;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 13:40:29
 * To change this template use File | Settings | File Templates.
 */
public class PyCallExpressionImpl extends PyElementImpl implements PyCallExpression {

  public PyCallExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyCallExpression(this);
  }

  @PsiCached
  @Nullable
  public PyExpression getCallee() {
    //return PsiTreeUtil.getChildOfType(this, PyReferenceExpression.class); what we call can be whatever expr, not always a ref
    return (PyExpression)getFirstChild();
  }

  @PsiCached
  public PyArgumentList getArgumentList() {
    return PyCallExpressionHelper.getArgumentList(this);
  }

  public void addArgument(PyExpression expression) {
    PyCallExpressionHelper.addArgument(this, getLanguage(), expression);
    /*
    PyExpression[] arguments = getArgumentList().getArguments();
    try {
      getLanguage().getElementGenerator()
        .insertItemIntoList(getProject(), this, arguments.length == 0 ? null : arguments[arguments.length - 1], expression);
    }
    catch (IncorrectOperationException e1) {
      throw new IllegalArgumentException(e1);
    }
    */
  }

  public PyMarkedFunction resolveCallee() {
    return PyCallExpressionHelper.resolveCallee(this);
  }

  @Nullable
  public PyElement resolveCallee2() {
    return PyCallExpressionHelper.resolveCallee2(this);
  }

  @Override
  public String toString() {
    return "PyCallExpression: " + PyUtil.getReadableRepr(getCallee(), true); //or: getCalledFunctionReference().getReferencedName();
  }

  public PyType getType() {
    PyExpression callee = getCallee();
    if (callee instanceof PyReferenceExpression) {
      // hardwired special cases
      if ("super".equals(callee.getText())) {
        PsiElement must_be_super_init = ((PyReferenceExpression)callee).resolve();
        if (must_be_super_init instanceof PyFunction) {
          PyClass must_be_super = ((PyFunction)must_be_super_init).getContainingClass();
          if (must_be_super == PyBuiltinCache.getInstance(getProject()).getClass("super")) {
            PyArgumentList arglist = getArgumentList();
            if (arglist != null) {
              PyExpression[] args = arglist.getArguments();
              if (args.length > 1) {
                PyExpression first_arg = args[0];
                if (first_arg instanceof PyReferenceExpression) {
                  PsiElement possible_class = ((PyReferenceExpression)first_arg).resolve();
                  if (possible_class instanceof PyClass && ((PyClass)possible_class).isNewStyleClass()) {
                    final PyClass first_class = (PyClass)possible_class;
                    // check 2nd argument, too; it should be an instance
                    PyExpression second_arg = args[1];
                    if (second_arg != null) {
                      PyType second_type = second_arg.getType();
                      if (second_type instanceof PyClassType) {
                        // imitate isinstance(second_arg, possible_class)
                        PyClass second_class = ((PyClassType)second_type).getPyClass();
                        assert second_class != null;
                        if (second_class.isSublclass(first_class)) {
                          // TODO: super(Foo, Bar) is a superclass of Foo directly preceding Bar in MRO
                          return new PyClassType(first_class, false); // super(Foo, self) has type of Foo, modulo __get__()
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      // normal cases
      ResolveResult[] targets = ((PyReferenceExpression)callee).multiResolve(false);
      if (targets.length > 0) {
        PsiElement target = targets[0].getElement();
        if (target instanceof PyClass) {
          return new PyClassType((PyClass) target, false); // we call a class name, that is, the constructor, we get an instance.
        }
        else if (target instanceof PyFunction && PyNames.INIT.equals(((PyFunction)target).getName())) {
          return new PyClassType(((PyFunction)target).getContainingClass(), false); // resolved to __init__, back to class
        }
        // TODO: look at well-known functions and their return types
        return PyReferenceExpressionImpl.getReferenceTypeFromProviders(target);
      }
    }
    if (callee == null) return null;
    else return callee.getType();
  }
}
