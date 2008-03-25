/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.java.stubs.impl.PsiParameterListStubImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaParameterListElementType extends JavaStubElementType<PsiParameterListStub, PsiParameterList> {
  public JavaParameterListElementType() {
    super("java.PARAMETER_LIST");
  }

  public PsiParameterList createPsi(final PsiParameterListStub stub) {
    throw new UnsupportedOperationException("createPsi is not implemented"); // TODO
  }

  public PsiParameterListStub createStub(final PsiParameterList psi, final StubElement parentStub) {
    return new PsiParameterListStubImpl(parentStub, this);
  }

  public String getExternalId() {
    return "java.PARAMETER_LIST";
  }

  public void serialize(final PsiParameterListStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
  }

  public PsiParameterListStub deserialize(final DataInputStream dataStream, final StubElement parentStub, final PersistentStringEnumerator nameStorage)
      throws IOException {
    return new PsiParameterListStubImpl(parentStub, this);
  }

  public void indexStub(final PsiParameterListStub stub, final IndexSink sink) {
  }
}