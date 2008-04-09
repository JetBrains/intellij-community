/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.cache.impl.repositoryCache.RecordUtil;
import com.intellij.psi.impl.cache.impl.repositoryCache.TypeInfo;
import com.intellij.psi.impl.java.stubs.impl.PsiParameterStubImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaParameterElementType extends JavaStubElementType<PsiParameterStub, PsiParameter> {
  public JavaParameterElementType() {
    super("PARAMETER");
  }

  public PsiParameter createPsi(final PsiParameterStub stub) {
    throw new UnsupportedOperationException("createPsi is not implemented"); // TODO
  }

  public PsiParameterStub createStub(final PsiParameter psi, final StubElement parentStub) {
    final TypeInfo type = TypeInfo.create(psi.getType(), psi.getTypeElement());
    return new PsiParameterStubImpl(parentStub, psi.getName(), type, psi.isVarArgs());
  }

  public void serialize(final PsiParameterStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
    DataInputOutputUtil.writeNAME(dataStream, stub.getName(), nameStorage);
    RecordUtil.writeTYPE(dataStream, stub.getParameterType(), nameStorage);
    dataStream.writeBoolean(stub.isParameterTypeEllipsis());
  }

  public PsiParameterStub deserialize(final DataInputStream dataStream, final StubElement parentStub, final PersistentStringEnumerator nameStorage)
      throws IOException {
    String name = DataInputOutputUtil.readNAME(dataStream, nameStorage);
    TypeInfo type = new TypeInfo();
    RecordUtil.readTYPE(dataStream, type, nameStorage);
    boolean isEll = dataStream.readBoolean();
    return new PsiParameterStubImpl(parentStub, name, type, isEll);
  }

  public void indexStub(final PsiParameterStub stub, final IndexSink sink) {
  }
}