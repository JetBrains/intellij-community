/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.cache.impl.repositoryCache.RecordUtil;
import com.intellij.psi.impl.java.stubs.impl.PsiModifierListStubImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaModifierListElementType extends JavaStubElementType<PsiModifierListStub, PsiModifierList> {
  public JavaModifierListElementType() {
    super("java.modlist");
  }

  public String getExternalId() {
    return "java.modlist";
  }

  public PsiModifierList createPsi(final PsiModifierListStub stub) {
    throw new UnsupportedOperationException("createPsi is not implemented"); // TODO
  }

  public PsiModifierListStub createStub(final PsiModifierList psi, final StubElement parentStub) {
    return new PsiModifierListStubImpl(parentStub, RecordUtil.packModifierList(psi));
  }

  public void serialize(final PsiModifierListStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
    DataInputOutputUtil.writeINT(dataStream, stub.getModifiersMask());
  }

  public PsiModifierListStub deserialize(final DataInputStream dataStream, final StubElement parentStub, final PersistentStringEnumerator nameStorage)
      throws IOException {
    return new PsiModifierListStubImpl(parentStub, DataInputOutputUtil.readINT(dataStream));
  }

  public void indexStub(final PsiModifierListStub stub, final IndexSink sink) {
  }
}