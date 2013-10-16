package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.impl.PyDecoratorImpl;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import org.jetbrains.annotations.NotNull;

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

  public PsiElement createElement(@NotNull ASTNode node) {
    return new PyDecoratorImpl(node);
  }

  public PyDecorator createPsi(@NotNull PyDecoratorStub stub) {
    return new PyDecoratorImpl(stub);
  }

  public PyDecoratorStub createStub(@NotNull PyDecorator psi, StubElement parentStub) {
    return new PyDecoratorStubImpl(psi.getQualifiedName(), parentStub);
  }

  public void serialize(@NotNull PyDecoratorStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    QualifiedName.serialize(stub.getQualifiedName(), dataStream);
  }

  @NotNull
  public PyDecoratorStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    QualifiedName q_name = QualifiedName.deserialize(dataStream);
    return new PyDecoratorStubImpl(q_name, parentStub);
  }

}
