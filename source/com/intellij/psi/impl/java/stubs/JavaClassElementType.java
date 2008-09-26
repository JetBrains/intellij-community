/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstantInitializer;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiClassStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaAnonymousClassBaseRefOccurenceIndex;
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex;
import com.intellij.psi.impl.source.PsiAnonymousClassImpl;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.PsiEnumConstantInitializerImpl;
import com.intellij.psi.impl.source.tree.java.AnonymousClassElement;
import com.intellij.psi.impl.source.tree.java.EnumConstantInitializerElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class JavaClassElementType extends JavaStubElementType<PsiClassStub, PsiClass> {
  public JavaClassElementType(@NotNull @NonNls final String id) {
    super(id);
  }

  public PsiClass createPsi(final PsiClassStub stub) {
    if (isCompiled(stub)) {
      return new ClsClassImpl(stub);
    }
    if (stub.isEnumConstantInitializer()) {
      return new PsiEnumConstantInitializerImpl(stub);
    }
    if (stub.isAnonymous()) {
      return new PsiAnonymousClassImpl(stub);
    }
    return new PsiClassImpl(stub);
  }

  public PsiClass createPsi(final ASTNode node) {
    if (node instanceof EnumConstantInitializerElement) {
      return new PsiEnumConstantInitializerImpl(node);
    }
    else if (node instanceof AnonymousClassElement) {
      return new PsiAnonymousClassImpl(node);
    }

    return new PsiClassImpl(node);
  }

  public PsiClassStub createStub(final PsiClass psi, final StubElement parentStub) {
    final boolean isAnonymous = psi instanceof PsiAnonymousClass;
    final boolean isEnumConst = psi instanceof PsiEnumConstantInitializer;

    byte flags = PsiClassStubImpl.packFlags(RecordUtil.isDeprecatedByDocComment(psi),
                                            psi.isInterface(),
                                            psi.isEnum(),
                                            isEnumConst,
                                            isAnonymous,
                                            psi.isAnnotationType(),
                                            isAnonymous && ((PsiAnonymousClass)psi).isInQualifiedNew(),
                                            RecordUtil.isDeprecatedByAnnotation(psi));

    String baseRef = isAnonymous ? ((PsiAnonymousClass)psi).getBaseClassReference().getText() : null;
    final JavaClassElementType type = typeForClass(isAnonymous, isEnumConst);
    return new PsiClassStubImpl(type, parentStub, psi.getQualifiedName(), psi.getName(), baseRef, flags);
  }

  public static JavaClassElementType typeForClass(final boolean anonymous, final boolean enumConst) {
    return enumConst
           ? JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER
           : anonymous ? JavaStubElementTypes.ANONYMOUS_CLASS : JavaStubElementTypes.CLASS;
  }


  public void serialize(final PsiClassStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeByte(((PsiClassStubImpl)stub).getFlags());
    if (!stub.isAnonymous()) {
      dataStream.writeName(stub.getName());
      dataStream.writeName(stub.getQualifiedName());
      dataStream.writeName(stub.getSourceFileName());
    }
    else {
      dataStream.writeName(stub.getBaseClassReferenceText());
    }
  }

  public PsiClassStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    byte flags = dataStream.readByte();

    final boolean isAnonymous = PsiClassStubImpl.isAnonymous(flags);
    final boolean isEnumConst = PsiClassStubImpl.isEnumConstInitializer(flags);
    final JavaClassElementType type = typeForClass(isAnonymous, isEnumConst);

    if (!isAnonymous) {
      StringRef name = dataStream.readName();
      StringRef qname = dataStream.readName();
      final StringRef sourceFileName = dataStream.readName();
      final PsiClassStubImpl classStub = new PsiClassStubImpl(type, parentStub, qname, name, null, flags);
      classStub.setSourceFileName(sourceFileName);
      return classStub;
    }
    else {
      StringRef baseref = dataStream.readName();
      return new PsiClassStubImpl(type, parentStub, null, null, baseref, flags);
    }
  }

  public void indexStub(final PsiClassStub stub, final IndexSink sink) {
    boolean isAnonymous = stub.isAnonymous();
    if (isAnonymous) {
      String baseref = stub.getBaseClassReferenceText();
      if (baseref != null) {
        sink.occurrence(JavaAnonymousClassBaseRefOccurenceIndex.KEY, PsiNameHelper.getShortClassName(baseref));
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

  public String getId(final PsiClassStub stub) {
    final String name = stub.getName();
    if (name != null) return name;

    return super.getId(stub);
  }
}