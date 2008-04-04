/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstantInitializer;
import com.intellij.psi.impl.java.stubs.impl.PsiClassStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaAnonymousClassBaseRefOccurenceIndex;
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaClassElementType extends JavaStubElementType<PsiClassStub, PsiClass> {
  public JavaClassElementType() {
    super("java.CLASS");
  }

  public String getExternalId() {
    return "java.CLASS";
  }

  public PsiClass createPsi(final PsiClassStub stub) {
    throw new UnsupportedOperationException("createPsi is not implemented"); // TODO
  }

  public PsiClassStub createStub(final PsiClass psi, final StubElement parentStub) {
    byte flags = PsiClassStubImpl.packFlags(psi.isDeprecated(),
                                            psi.isInterface(),
                                            psi.isEnum(),
                                            psi instanceof PsiEnumConstantInitializer,
                                            psi instanceof PsiAnonymousClass,
                                            psi.isAnnotationType(),
                                            psi instanceof PsiAnonymousClass && ((PsiAnonymousClass)psi).isInQualifiedNew());

    String baseRef = psi instanceof PsiAnonymousClass ? ((PsiAnonymousClass)psi).getBaseClassReference().getText() : null;
    return new PsiClassStubImpl(parentStub, psi.getQualifiedName(), psi.getName(), baseRef, flags);
  }


  public void serialize(final PsiClassStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
    dataStream.writeByte(((PsiClassStubImpl)stub).getFlags());
    if (!stub.isAnonymous()) {
      DataInputOutputUtil.writeNAME(dataStream, stub.getName(), nameStorage);
      DataInputOutputUtil.writeNAME(dataStream, stub.getQualifiedName(), nameStorage);
    }
    else {
      DataInputOutputUtil.writeNAME(dataStream, stub.getBaseClassReferenceText(), nameStorage);
    }
  }

  public PsiClassStub deserialize(final DataInputStream dataStream,
                                  final StubElement parentStub, final PersistentStringEnumerator nameStorage) throws IOException {
    byte flags = dataStream.readByte();

    if (PsiClassStubImpl.isAnonymous(flags)) {
      String baseref = DataInputOutputUtil.readNAME(dataStream, nameStorage);
      return new PsiClassStubImpl(parentStub, null, null, baseref, flags);
    }
    else {
      String name = DataInputOutputUtil.readNAME(dataStream, nameStorage);
      String qname = DataInputOutputUtil.readNAME(dataStream, nameStorage);
      return new PsiClassStubImpl(parentStub, qname, name, null, flags);
    }
  }

  public void indexStub(final PsiClassStub stub, final IndexSink sink) {
    boolean isAnonymous = stub.isAnonymous();
    if (isAnonymous) {
      String baseref = stub.getBaseClassReferenceText();
      if (baseref != null) {
        sink.occurence(JavaAnonymousClassBaseRefOccurenceIndex.KEY, baseref);
      }
    }
    else {
      final String shortname = stub.getName();
      if (shortname != null) {
        sink.occurence(JavaShortClassNameIndex.KEY, shortname);
      }

      final String fqn = stub.getQualifiedName();
      if (fqn != null) {
        sink.occurence(JavaFullClassNameIndex.KEY, fqn.hashCode());
      }
    }
  }
}