package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;
import org.jetbrains.annotations.NotNull;

/**
 * @author dcheryasov
 */
public class PyDecoratorListImpl extends PyBaseElementImpl<PyDecoratorListStub> implements PyDecoratorList{
  
  public PyDecoratorListImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyDecoratorList(this);
  }

  public PyDecoratorListImpl(final PyDecoratorListStub stub) {
    super(stub, PyElementTypes.DECORATOR_LIST);
  }

  @NotNull
  public PyDecorator[] getDecorators() {
    final PyDecorator[] decoarray = new PyDecorator[0];
    return getStubOrPsiChildren(PyElementTypes.DECORATOR_CALL, decoarray);
    //return decoarray;
  }

  @Override
  public PyDecorator findDecorator(String name) {
    final PyDecorator[] decorators = getDecorators();
    for (PyDecorator decorator : decorators) {
      final QualifiedName qName = decorator.getQualifiedName();
      if (qName != null && name.equals(qName.toString())) {
        return decorator;
      }
    }
    return null;
  }
}
