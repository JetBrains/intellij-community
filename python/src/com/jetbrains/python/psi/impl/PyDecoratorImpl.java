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
package com.jetbrains.python.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author dcheryasov
 */
public class PyDecoratorImpl extends StubBasedPsiElementBase<PyDecoratorStub> implements PyDecorator {

  public PyDecoratorImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyDecoratorImpl(PyDecoratorStub stub) {
    super(stub, PyElementTypes.DECORATOR_CALL);
  }

  /**
   * @return the name of decorator, without the "@". Stub is used if available.
   */
  @Override
  public String getName() {
    final QualifiedName qname = getQualifiedName();
    return qname != null ? qname.getLastComponent() : null;
  }

  @Nullable
  public PyFunction getTarget() {
    return PsiTreeUtil.getParentOfType(this, PyFunction.class);
  }

  public boolean isBuiltin() {
    ASTNode node = getNode().findChildByType(PythonDialectsTokenSetProvider.INSTANCE.getReferenceExpressionTokens());
    if (node != null) {
      PyReferenceExpression ref = (PyReferenceExpression)node.getPsi();
      PsiElement target = ref.getReference().resolve();
      return PyBuiltinCache.getInstance(this).isBuiltin(target);
    }
    return false;
  }

  public boolean hasArgumentList() {
    final ASTNode arglistNode = getNode().findChildByType(PyElementTypes.ARGUMENT_LIST);
    return (arglistNode != null) && (arglistNode.findChildByType(PyTokenTypes.LPAR) != null);
  }

  public QualifiedName getQualifiedName() {
    final PyDecoratorStub stub = getStub();
    if (stub != null) {
      return stub.getQualifiedName();
    }
    else {
      final PyReferenceExpression node = PsiTreeUtil.getChildOfType(this, PyReferenceExpression.class);
      if (node != null) {
        return node.asQualifiedName();
      }
      return null;
    }
  }

  public PyExpression getCallee() {
    try {
      return (PyExpression)getFirstChild().getNextSibling(); // skip the @ before call
    }
    catch (NullPointerException npe) { // no sibling
      return null;
    }
    catch (ClassCastException cce) { // error node instead
      return null;
    }
  }

  @NotNull
  @Override
  public List<PyRatedMarkedCallee> multiResolveRatedCallee(@NotNull PyResolveContext resolveContext, int implicitOffset) {
    final Function<PyRatedMarkedCallee, PyRatedMarkedCallee> mapping = ratedMarkedCallee -> {
      if (!hasArgumentList()) {
        // NOTE: that +1 thing looks fishy
        final PyMarkedCallee oldMarkedCallee = ratedMarkedCallee.getMarkedCallee();
        final PyMarkedCallee newMarkedCallee = new PyMarkedCallee(oldMarkedCallee.getCallable(),
                                                                  oldMarkedCallee.getModifier(),
                                                                  oldMarkedCallee.getImplicitOffset() + 1,
                                                                  oldMarkedCallee.isImplicitlyResolved());

        return new PyRatedMarkedCallee(newMarkedCallee, ratedMarkedCallee.getRate());
      }

      return ratedMarkedCallee;
    };

    return ContainerUtil.map(PyCallExpressionHelper.multiResolveRatedCallee(this, resolveContext, implicitOffset), mapping);
  }

  @NotNull
  @Override
  public List<PyArgumentsMapping> multiMapArguments(@NotNull PyResolveContext resolveContext, int implicitOffset) {
    return PyCallExpressionHelper.multiMapArguments(this, resolveContext, implicitOffset);
  }

  @Override
  public String toString() {
    return "PyDecorator: @" + PyUtil.getReadableRepr(getCallee(), true); //getCalledFunctionReference().getReferencedName();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    final ASTNode node = getNode();
    final ASTNode nameNode = node.findChildByType(PyTokenTypes.IDENTIFIER);
    if (nameNode != null) {
      final ASTNode nameElement = PyUtil.createNewName(this, name);
      node.replaceChild(nameNode, nameElement);
      return this;
    }
    else {
      throw new IncorrectOperationException("No name node");
    }
  }

  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return PyCallExpressionHelper.getCallType(this, context);
  }
}
