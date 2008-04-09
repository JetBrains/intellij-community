/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.cache.impl.repositoryCache.RecordUtil;
import com.intellij.psi.impl.cache.impl.repositoryCache.TypeInfo;
import com.intellij.psi.impl.java.stubs.impl.PsiMethodStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaMethodNameIndex;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaMethodElementType extends JavaStubElementType<PsiMethodStub, PsiMethod> {
  public JavaMethodElementType() {
    super("METHOD");
  }

  public PsiMethod createPsi(final PsiMethodStub stub) {
    throw new UnsupportedOperationException("createPsi is not implemented"); // TODO
  }

  public PsiMethodStub createStub(final PsiMethod psi, final StubElement parentStub) {
    final byte flags = PsiMethodStubImpl.packFlags(psi.isConstructor(), psi instanceof PsiAnnotationMethod, psi.isVarArgs(), psi.isDeprecated());

    String defValueText = null;
    if (psi instanceof PsiAnnotationMethod) {
      PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)psi).getDefaultValue();
      if (defaultValue != null) {
        defValueText = defaultValue.getText();
      }
    }

    return new PsiMethodStubImpl(parentStub, psi.getName(),
                                 TypeInfo.create(psi.getReturnType(), psi.getReturnTypeElement()),
                                 flags,
                                 defValueText);
  }

  public void serialize(final PsiMethodStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
    DataInputOutputUtil.writeNAME(dataStream, stub.getName(), nameStorage);
    RecordUtil.writeTYPE(dataStream, stub.getReturnTypeText(), nameStorage);
    dataStream.writeByte(((PsiMethodStubImpl)stub).getFlags());
    if (stub.isAnnotationMethod()) {
      DataInputOutputUtil.writeNAME(dataStream, stub.getDefaultValueText(), nameStorage);
    }
  }

  public PsiMethodStub deserialize(final DataInputStream dataStream,
                                   final StubElement parentStub, final PersistentStringEnumerator nameStorage) throws IOException {
    String name = DataInputOutputUtil.readNAME(dataStream, nameStorage);
    final TypeInfo type = new TypeInfo();
    RecordUtil.readTYPE(dataStream, type, nameStorage);
    byte flags = dataStream.readByte();
    if (PsiMethodStubImpl.isAnnotationMethod(flags)) {
      final String defaultMethodValue = DataInputOutputUtil.readNAME(dataStream, nameStorage);
      return new PsiMethodStubImpl(parentStub, name, type, flags, defaultMethodValue);
    }
    return new PsiMethodStubImpl(parentStub, name, type, flags, null);
  }

  public void indexStub(final PsiMethodStub stub, final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurence(JavaMethodNameIndex.KEY, name);
    }
  }
}