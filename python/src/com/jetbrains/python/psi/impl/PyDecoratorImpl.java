package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: dcheryasov
 * Date: Sep 26, 2008
 * Time: 8:14:07 PM
 * To change this template use File | Settings | File Templates.
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
    final PyDecoratorStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    else {
      ASTNode node = getNode().findChildByType(PyElementTypes.REFERENCE_EXPRESSION);
      if (node != null) {
        node = node.findChildByType(PyTokenTypes.IDENTIFIER);
        if (node != null) return node.getText();
      }
      return null;
    }
  }

  @Nullable
  public PyFunction getTarget() {
    return PsiTreeUtil.getParentOfType(this, PyFunction.class);
  }

  public boolean isBuiltin() {
    final PyDecoratorStub stub = getStub();
    if (stub != null) {
      return stub.isBuiltin();
    }
    else {
      ASTNode node = getNode().findChildByType(PyElementTypes.REFERENCE_EXPRESSION);
      if (node != null) {
        PyReferenceExpression ref = (PyReferenceExpression)node.getPsi();
        PsiElement target = ref.resolve();
        return PyBuiltinCache.hasInBuiltins(target);
      }
      return false;
    }
  }

  public boolean hasArgumentList() {
    ASTNode arglist_node = getNode().findChildByType(PyElementTypes.ARGUMENT_LIST);
    return (arglist_node != null) && (arglist_node.findChildByType(PyTokenTypes.LPAR) != null);
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

  public void addArgument(PyExpression expression) {
    PyCallExpressionHelper.addArgument(this, getLanguage(), expression);
  }

  public PyMarkedFunction resolveCallee() {
    PyMarkedFunction callee = PyCallExpressionHelper.resolveCallee(this);
    if (callee == null) return null;
    if (! hasArgumentList()) {
      callee = new PyMarkedFunction(callee.getFunction(), callee.getFlags(), callee.getImplicitOffset() + 1);
    }
    return callee;
  }

  @Override
  public String toString() {
    return "PyDecorator: @" + PyUtil.getReadableRepr(getCallee(), true); //getCalledFunctionReference().getReferencedName();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    final ASTNode node = getNode();
    final ASTNode name_node = node.findChildByType(PyTokenTypes.IDENTIFIER);
    if (name_node != null) {
      final ASTNode nameElement = getLanguage().getElementGenerator().createNameIdentifier(getProject(), name);
      node.replaceChild(name_node, nameElement);
      return this;
    }
    else throw new IncorrectOperationException("No name node");
  }

  // TODO: create a custom version of public PyType getType()
  public PyType getType() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
