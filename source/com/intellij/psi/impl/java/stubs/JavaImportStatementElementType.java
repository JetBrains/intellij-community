/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.impl.java.stubs.impl.PsiImportStatementStubImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaImportStatementElementType extends JavaStubElementType<PsiImportStatementStub, PsiImportStatementBase> {
  public JavaImportStatementElementType(@NonNls @NotNull final String id) {
    super(id);
  }

  public PsiImportStatementBase createPsi(final PsiImportStatementStub stub) {
    throw new UnsupportedOperationException("createPsi is not implemented"); // TODO
  }

  public PsiImportStatementStub createStub(final PsiImportStatementBase psi, final StubElement parentStub) {
    final byte flags = PsiImportStatementStubImpl.packFlags(psi.isOnDemand(), psi instanceof PsiImportStaticStatement);
    final PsiJavaCodeReferenceElement ref = psi.getImportReference();
    return new PsiImportStatementStubImpl(parentStub, ref != null ? ref.getCanonicalText() : null, flags);
  }

  public void serialize(final PsiImportStatementStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
    dataStream.writeByte(((PsiImportStatementStubImpl)stub).getFlags());
    DataInputOutputUtil.writeNAME(dataStream, stub.getImportReferenceText(), nameStorage);
  }

  public PsiImportStatementStub deserialize(final DataInputStream dataStream,
                                            final StubElement parentStub,
                                            final PersistentStringEnumerator nameStorage) throws IOException {
    byte flags = dataStream.readByte();
    String reftext = DataInputOutputUtil.readNAME(dataStream, nameStorage);
    return new PsiImportStatementStubImpl(parentStub, reftext, flags);
  }

  public void indexStub(final PsiImportStatementStub stub, final IndexSink sink) {
  }
}