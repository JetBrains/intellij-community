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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaClassElementType extends JavaStubElementType<PsiClassStub, PsiClass> {
  public JavaClassElementType(@NotNull @NonNls final String id) {
    super(id);
  }

  public PsiClass createPsi(final PsiClassStub stub) {
    throw new UnsupportedOperationException("createPsi is not implemented"); // TODO
  }

  public PsiClassStub createStub(final PsiClass psi, final StubElement parentStub) {
    final boolean isAnonymous = psi instanceof PsiAnonymousClass;
    final boolean isEnumConst = psi instanceof PsiEnumConstantInitializer;

    byte flags = PsiClassStubImpl.packFlags(psi.isDeprecated(),
                                            psi.isInterface(),
                                            psi.isEnum(),
                                            isEnumConst,
                                            isAnonymous,
                                            psi.isAnnotationType(),
                                            isAnonymous && ((PsiAnonymousClass)psi).isInQualifiedNew());

    String baseRef = isAnonymous ? ((PsiAnonymousClass)psi).getBaseClassReference().getText() : null;
    final JavaClassElementType type = typeForClass(isAnonymous, isEnumConst);
    return new PsiClassStubImpl(type, parentStub, psi.getQualifiedName(), psi.getName(), baseRef, flags);
  }

  public static JavaClassElementType typeForClass(final boolean anonymous, final boolean enumConst) {
    return enumConst
           ? JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER
           : anonymous ? JavaStubElementTypes.ANONYMOUS_CLASS : JavaStubElementTypes.CLASS;
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

    final boolean isAnonymous = PsiClassStubImpl.isAnonymous(flags);
    final boolean isEnumConst = PsiClassStubImpl.isEnumConstInitializer(flags);
    final JavaClassElementType type = typeForClass(isAnonymous, isEnumConst);

    if (!isAnonymous) {
      String name = DataInputOutputUtil.readNAME(dataStream, nameStorage);
      String qname = DataInputOutputUtil.readNAME(dataStream, nameStorage);
      return new PsiClassStubImpl(type, parentStub, qname, name, null, flags);
    }
    else {
      String baseref = DataInputOutputUtil.readNAME(dataStream, nameStorage);
      return new PsiClassStubImpl(type, parentStub, null, null, baseref, flags);
    }
  }

  public void indexStub(final PsiClassStub stub, final IndexSink sink) {
    boolean isAnonymous = stub.isAnonymous();
    if (isAnonymous) {
      String baseref = stub.getBaseClassReferenceText();
      if (baseref != null) {
        sink.occurrence(JavaAnonymousClassBaseRefOccurenceIndex.KEY, baseref);
      }
    }
    else {
      final String shortname = stub.getName();
      if (shortname != null) {
        sink.occurrence(JavaShortClassNameIndex.KEY, shortname);
      }

      final String fqn = stub.getQualifiedName();
      if (fqn != null) {
        sink.occurrence(JavaFullClassNameIndex.KEY, fqn.hashCode());
      }
    }
  }
}