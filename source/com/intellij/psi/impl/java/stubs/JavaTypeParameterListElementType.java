/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.java.stubs.impl.PsiTypeParameterListStubImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaTypeParameterListElementType extends JavaStubElementType<PsiTypeParameterListStub, PsiTypeParameterList> {
  public JavaTypeParameterListElementType() {
    super("java.TYPE_PARAMETER_LIST");
  }

  public PsiTypeParameterList createPsi(final PsiTypeParameterListStub stub) {
    throw new UnsupportedOperationException("createPsi is not implemented"); // TODO
  }

  public PsiTypeParameterListStub createStub(final PsiTypeParameterList psi, final StubElement parentStub) {
    return new PsiTypeParameterListStubImpl(parentStub, this);
  }

  public String getExternalId() {
    return "java.TYPE_PARAMETER_LIST";
  }

  public void serialize(final PsiTypeParameterListStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
  }

  public PsiTypeParameterListStub deserialize(final DataInputStream dataStream, final StubElement parentStub, final PersistentStringEnumerator nameStorage)
      throws IOException {
    return new PsiTypeParameterListStubImpl(parentStub, this);
  }

  public void indexStub(final PsiTypeParameterListStub stub, final IndexSink sink) {
  }
}