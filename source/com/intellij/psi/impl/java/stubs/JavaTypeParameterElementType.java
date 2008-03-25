/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.java.stubs.impl.PsiTypeParameterStubImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaTypeParameterElementType extends JavaStubElementType<PsiTypeParameterStub, PsiTypeParameter> {
  public JavaTypeParameterElementType() {
    super("java.TYPE_PARAMETR");
  }

  public PsiTypeParameter createPsi(final PsiTypeParameterStub stub) {
    throw new UnsupportedOperationException("createPsi is not implemented"); // TODO
  }

  public PsiTypeParameterStub createStub(final PsiTypeParameter psi, final StubElement parentStub) {
    return new PsiTypeParameterStubImpl(parentStub, this, psi.getName());
  }

  public String getExternalId() {
    return "java.TYPE_PARAMETR";
  }

  public void serialize(final PsiTypeParameterStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
    DataInputOutputUtil.writeNAME(dataStream, stub.getName(), nameStorage);
  }

  public PsiTypeParameterStub deserialize(final DataInputStream dataStream, final StubElement parentStub, final PersistentStringEnumerator nameStorage)
      throws IOException {
    return new PsiTypeParameterStubImpl(parentStub, this, DataInputOutputUtil.readNAME(dataStream, nameStorage));
  }

  public void indexStub(final PsiTypeParameterStub stub, final IndexSink sink) {
  }
}