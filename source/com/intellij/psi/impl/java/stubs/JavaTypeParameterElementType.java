/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.compiled.ClsTypeParameterImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiTypeParameterStubImpl;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;

import java.io.IOException;

public class JavaTypeParameterElementType extends JavaStubElementType<PsiTypeParameterStub, PsiTypeParameter> {
  public JavaTypeParameterElementType() {
    super("TYPE_PARAMETR");
  }

  public PsiTypeParameter createPsi(final PsiTypeParameterStub stub) {
    if (isCompiled(stub)) {
      return new ClsTypeParameterImpl(stub);
    }
    else {
      return new PsiTypeParameterImpl(stub);
    }
  }

  public PsiTypeParameter createPsi(final ASTNode node) {
    return new PsiTypeParameterImpl(node);
  }

  public PsiTypeParameterStub createStub(final PsiTypeParameter psi, final StubElement parentStub) {
    StringRef name = StringRef.fromString(psi.getName());
    return new PsiTypeParameterStubImpl(parentStub, name);
  }

  public void serialize(final PsiTypeParameterStub stub, final StubOutputStream dataStream) throws IOException {
    String name = stub.getName();
    dataStream.writeName(name);
  }

  public PsiTypeParameterStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    StringRef name = dataStream.readName();
    return new PsiTypeParameterStubImpl(parentStub, name);
  }

  public void indexStub(final PsiTypeParameterStub stub, final IndexSink sink) {
  }
}
