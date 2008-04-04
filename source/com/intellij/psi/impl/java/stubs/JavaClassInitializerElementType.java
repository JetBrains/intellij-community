/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.impl.java.stubs.impl.PsiClassInitializerStubImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaClassInitializerElementType extends JavaStubElementType<PsiClassInitializerStub, PsiClassInitializer> {
  public JavaClassInitializerElementType() {
    super("java.CLASS_INITIALIZER");
  }

  public PsiClassInitializer createPsi(final PsiClassInitializerStub stub) {
    throw new UnsupportedOperationException("createPsi is not implemented"); // TODO
  }

  public PsiClassInitializerStub createStub(final PsiClassInitializer psi, final StubElement parentStub) {
    return new PsiClassInitializerStubImpl(parentStub);
  }

  public String getExternalId() {
    return "java.CLASS_INITIALIZER";
  }

  public void serialize(final PsiClassInitializerStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
  }

  public PsiClassInitializerStub deserialize(final DataInputStream dataStream, final StubElement parentStub, final PersistentStringEnumerator nameStorage)
      throws IOException {
    return new PsiClassInitializerStubImpl(parentStub);
  }

  public void indexStub(final PsiClassInitializerStub stub, final IndexSink sink) {
  }
}