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
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

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
    PyArgumentList arglist = PsiTreeUtil.getChildOfType(this, PyArgumentList.class);
    return arglist;
  }

  public void addArgument(PyExpression expression) {
    PyExpression[] arguments = getArgumentList().getArguments();
    try {
      getLanguage().getElementGenerator()
        .insertItemIntoList(getProject(), this, arguments.length == 0 ? null : arguments[arguments.length - 1], expression);
    }
    catch (IncorrectOperationException e1) {
      throw new IllegalArgumentException(e1);
    }
  }

  public PyMarkedFunction resolveCallee() {
    PyExpression calleeReference = getCallee();
    if (calleeReference != null) {
      PsiReference cref = calleeReference.getReference();
      if (cref != null) {
        PyElement resolved = (PyElement) cref.resolve();
        if (resolved != null) {
          EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
          //boolean is_inst = isByInstance();
          if (isByInstance()) flags.add(Flag.IMPLICIT_FIRST_ARG);
          if (resolved instanceof PyClass) { // constructor call
            final PyClass cls = (PyClass)resolved;
            resolved = cls.findMethodByName("__init__");  // XXX move this name to PyNames
            //is_inst |= true;
            flags.add(Flag.IMPLICIT_FIRST_ARG);
          }
          if (resolved != null) {
            // look for closest decorator
            // TODO: look for all decorators
            // XXX disuse PyDecoratedFunction, use PyDecorator
            PsiElement parent = resolved.getParent();
            if (parent instanceof PyDecoratedFunction) {
              final PyDecoratedFunction decorated = (PyDecoratedFunction)parent;
              PsiElement decorator = PsiTreeUtil.getChildOfType(decorated, PyReferenceExpression.class);
              if (decorator != null) { // just in case
                String deco_name = decorator.getText();
                @NonNls final String STATICMETHOD = "staticmethod"; // TODO: must go to function
                @NonNls final String CLASSMETHOD = "classmethod";
                if (STATICMETHOD.equals(deco_name)) {
                  flags.add(Flag.STATICMETHOD);
                  flags.remove(Flag.IMPLICIT_FIRST_ARG);
                }
                else if (CLASSMETHOD.equals(deco_name)) {
                  flags.add(Flag.CLASSMETHOD);
                }
                // else could be custom decorator processing
              }
            }
          }
          if (!(resolved instanceof PyFunction)) return null; // omg, bogus __init__
          return new PyMarkedFunction((PyFunction) resolved, flags);
        }
      }
    }
    return null;
  }

  public PyElement resolveCallee2() {
    PyExpression calleeReference = getCallee();
    return (PyElement) calleeReference.getReference().resolve();
  }

  protected boolean isByInstance() {
    PyExpression callee = getCallee();
    if (callee instanceof PyReferenceExpression) {
      PyExpression qualifier = ((PyReferenceExpression)callee).getQualifier();
      if (qualifier != null) {
        PyType type = qualifier.getType();
        if ((type instanceof PyClassType) && (!((PyClassType)type).isDefinition())) {
          // we're calling an instance method of qualifier
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return "PyCallExpression: " + PyUtil.getReadableRepr(getCallee(), true); //getCalledFunctionReference().getReferencedName();
  }

  public PyType getType() {
    PyExpression callee = getCallee();
    if (callee instanceof PyReferenceExpression) {
      PsiElement target = ((PyReferenceExpression)callee).resolve();
      if (target instanceof PyClass) {
        return new PyClassType((PyClass) target, false); // we call a class name, that is, the constructor, we get an instance.
      }
      // TODO: look at well-known functions and their return types
      return PyReferenceExpressionImpl.getReferenceTypeFromProviders(target);
    }
    return callee.getType();
  }
}
