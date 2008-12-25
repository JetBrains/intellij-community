package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;
import com.jetbrains.python.psi.types.PyDecorator;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: dcheryasov
 * Date: Sep 28, 2008
 */
public class PyDecoratorListImpl extends PyBaseElementImpl<PyDecoratorListStub> implements PyDecoratorList{
  
  public PyDecoratorListImpl(ASTNode astNode) {
    super(astNode);
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
}
