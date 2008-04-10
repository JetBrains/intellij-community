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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaClassReferenceListElementType extends JavaStubElementType<PsiClassReferenceListStub, PsiReferenceList> {
  public JavaClassReferenceListElementType(@NotNull @NonNls String id) {
    super(id);
  }

  public PsiReferenceList createPsi(final PsiClassReferenceListStub stub) {
    throw new UnsupportedOperationException("createPsi is not implemented"); // TODO
  }

  public PsiClassReferenceListStub createStub(final PsiReferenceList psi, final StubElement parentStub) {
    return new PsiClassReferenceListStubImpl(roleToElementType(psi.getRole()), parentStub, getTexts(psi), psi.getRole());
  }

  private static JavaClassReferenceListElementType roleToElementType(final PsiReferenceList.Role role) {
    switch (role) {
      case EXTENDS_BOUNDS_LIST:
        return JavaStubElementTypes.EXTENDS_BOUND_LIST;
      case EXTENDS_LIST:
        return JavaStubElementTypes.EXTENDS_LIST;
      case IMPLEMENTS_LIST:
        return JavaStubElementTypes.IMPLEMENTS_LIST;
      case THROWS_LIST:
        return JavaStubElementTypes.THROWS_LIST;
    }

    throw new RuntimeException("Unknown role: " + role);
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

  public void serialize(final PsiClassReferenceListStub stub,
                        final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage) throws IOException {
    dataStream.writeByte(encodeRole(stub.getRole()));
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

    final PsiReferenceList.Role decodedRole = decodeRole(role);
    return new PsiClassReferenceListStubImpl(roleToElementType(decodedRole), parentStub, names, decodedRole);
  }

  private static PsiReferenceList.Role decodeRole(int code) {
    switch (code) {
      case 0: return PsiReferenceList.Role.EXTENDS_LIST;
      case 1: return PsiReferenceList.Role.IMPLEMENTS_LIST;
      case 2: return PsiReferenceList.Role.THROWS_LIST;
      case 3: return PsiReferenceList.Role.EXTENDS_BOUNDS_LIST;

      default:
        throw new RuntimeException("Unknown role code: " + code);
    }
  }

  private static byte encodeRole(PsiReferenceList.Role role) {
    switch (role) {
      case EXTENDS_LIST:         return 0;
      case IMPLEMENTS_LIST:      return 1;
      case THROWS_LIST:          return 2;
      case EXTENDS_BOUNDS_LIST:  return 3;

      default:
        throw new RuntimeException("Unknown role code: " + role);
    }
  }

  public void indexStub(final PsiClassReferenceListStub stub, final IndexSink sink) {
    final PsiReferenceList.Role role = stub.getRole();
    if (role == PsiReferenceList.Role.EXTENDS_LIST || role == PsiReferenceList.Role.IMPLEMENTS_LIST) {
      final String[] names = stub.getReferencedNames();
      for (String name : names) {
        sink.occurrence(JavaSuperClassNameOccurenceIndex.KEY, name);
      }
    }
  }
}