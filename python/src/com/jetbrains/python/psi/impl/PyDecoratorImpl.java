package com.jetbrains.python.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
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
      return PyBuiltinCache.getInstance(this).hasInBuiltins(target);
    }
    return false;
  }

  public boolean hasArgumentList() {
    ASTNode arglist_node = getNode().findChildByType(PyElementTypes.ARGUMENT_LIST);
    return (arglist_node != null) && (arglist_node.findChildByType(PyTokenTypes.LPAR) != null);
  }

  public QualifiedName getQualifiedName() {
    final PyDecoratorStub stub = getStub();
    if (stub != null) {
      return stub.getQualifiedName();
    }
    else {
      PyReferenceExpression node = PsiTreeUtil.getChildOfType(this, PyReferenceExpression.class);
      if (node != null) {
        List<PyExpression> parts = PyResolveUtil.unwindQualifiers(node);
        if (parts != null) {
          //Collections.reverse(parts);
          return PyQualifiedNameFactory.fromReferenceChain(parts);
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

  @Override
  public <T extends PsiElement> T getArgument(int index, Class<T> argClass) {
    PyExpression[] args = getArguments();
    return args.length >= index && argClass.isInstance(args[index]) ? argClass.cast(args[index]) : null;
  }

  @Override
  public <T extends PsiElement> T getArgument(int index, String keyword, Class<T> argClass) {
    final PyExpression argument = getKeywordArgument(keyword);
    if (argument != null) {
      return argClass.isInstance(argument) ? argClass.cast(argument) : null;
    }
    return getArgument(index, argClass);
  }

  @Override
  public PyExpression getKeywordArgument(String keyword) {
    return PyCallExpressionHelper.getKeywordArgument(this, keyword);
  }

  public void addArgument(PyExpression expression) {
    PyCallExpressionHelper.addArgument(this, expression);
  }

  public PyMarkedCallee resolveCallee(PyResolveContext resolveContext) {
    return resolveCallee(resolveContext, 0);
  }
  public PyMarkedCallee resolveCallee(PyResolveContext resolveContext, int offset) {
    PyMarkedCallee callee = PyCallExpressionHelper.resolveCallee(this, resolveContext);
    if (callee == null) return null;
    if (!hasArgumentList()) {
      // NOTE: that +1 thing looks fishy
      callee = new PyMarkedCallee(callee.getCallable(), callee.getModifier(), callee.getImplicitOffset() + 1, callee.isImplicitlyResolved());
    }
    return callee;
  }

  @Override
  public Callable resolveCalleeFunction(PyResolveContext resolveContext) {
    return PyCallExpressionHelper.resolveCalleeFunction(this, resolveContext);
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
    else {
      throw new IncorrectOperationException("No name node");
    }
  }

  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return PyCallExpressionHelper.getCallType(this, context);
  }
}
