package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyDecoratorImpl;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import com.jetbrains.python.psi.types.PyDecorator;

import java.io.IOException;

/**
 * Actual serialized data of a decorator call.
 * User: dcheryasov
 * Date: Dec 18, 2008 9:09:57 PM
 */
public class PyDecoratorCallElementType extends PyStubElementType<PyDecoratorStub, PyDecorator>  {
  public PyDecoratorCallElementType() {
    super("DECORATOR_CALL");
  }

  public PsiElement createElement(ASTNode node) {
    return new PyDecoratorImpl(node);
  }

  public PyDecorator createPsi(PyDecoratorStub stub) {
    return new PyDecoratorImpl(stub);
  }

  public PyDecoratorStub createStub(PyDecorator psi, StubElement parentStub) {
    boolean is_builtin = false;
    PyExpression target = psi.getCallee(); 
    if (target != null) {
       is_builtin = PyBuiltinCache.hasInBuiltins(target);
    }
    return new PyDecoratorStubImpl(psi.getName(), is_builtin, parentStub);
  }

  public void serialize(PyDecoratorStub stub, StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeBoolean(stub.isBuiltin());
  }

  public PyDecoratorStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    boolean builtin = dataStream.readBoolean();
    return new PyDecoratorStubImpl(name, builtin, parentStub);
  }

}
