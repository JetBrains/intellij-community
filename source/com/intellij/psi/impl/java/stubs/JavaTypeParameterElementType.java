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
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;

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
    return new PsiTypeParameterStubImpl(parentStub, psi.getName());
  }

  public void serialize(final PsiTypeParameterStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeName(stub.getName());
  }

  public PsiTypeParameterStub deserialize(final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    return new PsiTypeParameterStubImpl(parentStub, dataStream.readName());
  }

  public void indexStub(final PsiTypeParameterStub stub, final IndexSink sink) {
  }
}