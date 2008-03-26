/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.cache.impl.repositoryCache.RecordUtil;
import com.intellij.psi.impl.java.stubs.impl.PsiClassReferenceListStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaSuperClassNameOccurenceIndex;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaClassReferenceListElementType extends JavaStubElementType<PsiClassReferenceListStub, PsiReferenceList> {
  public JavaClassReferenceListElementType() {
    super("java.REFLIST");
  }

  public PsiReferenceList createPsi(final PsiClassReferenceListStub stub) {
    throw new UnsupportedOperationException("createPsi is not implemented"); // TODO
  }

  public PsiClassReferenceListStub createStub(final PsiReferenceList psi, final StubElement parentStub) {
    return new PsiClassReferenceListStubImpl(parentStub, this, getTexts(psi), psi.getRole());
  }

  private static String[] getTexts(PsiReferenceList psi) {
    final PsiJavaCodeReferenceElement[] refs = psi.getReferenceElements();
    String[] texts = new String[refs.length];
    for (int i = 0; i < refs.length; i++) {
      PsiJavaCodeReferenceElement ref = refs[i];
      texts[i] = ref instanceof PsiCompiledElement ? ref.getCanonicalText() : ref.getText();
    }
    return texts;
  }

  public String getExternalId() {
    return "java.REFLIST";
  }

  public void serialize(final PsiClassReferenceListStub stub,
                        final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage) throws IOException {
    final PsiReferenceList.Role role = stub.getRole();
    byte encodedRole = role == PsiReferenceList.Role.EXTENDS_LIST ? 0 : role == PsiReferenceList.Role.IMPLEMENTS_LIST ? (byte)1 : 2;
    dataStream.writeByte(encodedRole);
    final String[] names = stub.getReferencedNames();
    DataInputOutputUtil.writeINT(dataStream, names.length);
    for (String name : names) {
      DataInputOutputUtil.writeNAME(dataStream, name, nameStorage);
    }
  }

  public PsiClassReferenceListStub deserialize(final DataInputStream dataStream, final StubElement parentStub, final PersistentStringEnumerator nameStorage)
      throws IOException {
    byte role = dataStream.readByte();
    int len = DataInputOutputUtil.readINT(dataStream);
    String[] names = RecordUtil.createStringArray(len);
    for (int i = 0; i < names.length; i++) {
      names[i] = DataInputOutputUtil.readNAME(dataStream, nameStorage);
    }

    return new PsiClassReferenceListStubImpl(parentStub, this, names, role == 0
                                                                      ? PsiReferenceList.Role.EXTENDS_LIST
                                                                      : role == 1
                                                                        ? PsiReferenceList.Role.IMPLEMENTS_LIST
                                                                        : PsiReferenceList.Role.THROWS_LIST);
  }

  public void indexStub(final PsiClassReferenceListStub stub, final IndexSink sink) {
    final PsiReferenceList.Role role = stub.getRole();
    if (role == PsiReferenceList.Role.EXTENDS_LIST || role == PsiReferenceList.Role.IMPLEMENTS_LIST) {
      final String[] names = stub.getReferencedNames();
      for (String name : names) {
        sink.occurence(JavaSuperClassNameOccurenceIndex.KEY, name);
      }
    }
  }
}