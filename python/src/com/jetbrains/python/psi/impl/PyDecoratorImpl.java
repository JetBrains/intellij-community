package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author dcheryasov
 */
public class PyDecoratorImpl extends PyPresentableElementImpl<PyDecoratorStub> implements PyDecorator {

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
    final PyQualifiedName qname = getQualifiedName();
    return qname != null? qname.getLastComponent() : null;
  }

  @Nullable
  public PyFunction getTarget() {
    return PsiTreeUtil.getParentOfType(this, PyFunction.class);
  }

  public boolean isBuiltin() {
    ASTNode node = getNode().findChildByType(PyElementTypes.REFERENCE_EXPRESSION);
    if (node != null) {
      PyReferenceExpression ref = (PyReferenceExpression)node.getPsi();
      PsiElement target = ref.getReference().resolve();
      return PyBuiltinCache.getInstance(this).hasInBuiltins(target);
    }
    return false;
  }

  public boolean hasArgumentList() {
    ASTNode arglist_node = getNode().findChildByType(PyElementTypes.ARGUMENT_LIST);
    return (arglist_node != null) && (arglist_node.findChildByType(PyTokenTypes.LPAR) != null);
  }

  public PyQualifiedName getQualifiedName() {
    final PyDecoratorStub stub = getStub();
    if (stub != null) {
      return stub.getQualifiedName();
    }
    else {
      PyReferenceExpression node = PsiTreeUtil.getChildOfType(this, PyReferenceExpression.class);
      if (node != null) {
        List<PyReferenceExpression> parts = PyResolveUtil.unwindQualifiers(node);
        if (parts != null) {
          //Collections.reverse(parts);
          return PyQualifiedName.fromReferenceChain(parts);
        }
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

  @Nullable
  public PyArgumentList getArgumentList() {
    return PsiTreeUtil.getChildOfType(this, PyArgumentList.class);
  }

  @NotNull
  public PyExpression[] getArguments() {
    final PyArgumentList argList = getArgumentList();
    return argList != null ? argList.getArguments() : PyExpression.EMPTY_ARRAY;
  }

  public void addArgument(PyExpression expression) {
    PyCallExpressionHelper.addArgument(this, expression);
  }

  public PyMarkedCallee resolveCallee(TypeEvalContext context) {
    PyMarkedCallee callee = PyCallExpressionHelper.resolveCallee(this, context);
    if (callee == null) return null;
    if (! hasArgumentList()) {
      // NOTE: that +1 thing looks fishy
      callee = new PyMarkedCallee(callee.getCallable(), callee.getFlags(), callee.getImplicitOffset() + 1, callee.isImplicitlyResolved());
    }
    return callee;
  }

  public boolean isCalleeText(@NotNull String... nameCandidates) {
    return PyCallExpressionHelper.isCalleeText(this, nameCandidates);
  }

  @Override
  public String toString() {
    return "PyDecorator: @" + PyUtil.getReadableRepr(getCallee(), true); //getCalledFunctionReference().getReferencedName();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    final ASTNode node = getNode();
    final ASTNode name_node = node.findChildByType(PyTokenTypes.IDENTIFIER);
    if (name_node != null) {
      final ASTNode nameElement = PyElementGenerator.getInstance(getProject()).createNameIdentifier(name);
      node.replaceChild(name_node, nameElement);
      return this;
    }
    else throw new IncorrectOperationException("No name node");
  }

  // TODO: create a custom version of public PyType getType()
  public PyType getType(@NotNull TypeEvalContext context) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
