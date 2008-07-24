/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiMethodStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaMethodNameIndex;
import com.intellij.psi.impl.source.PsiAnnotationMethodImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.impl.source.tree.java.AnnotationMethodElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

public class JavaMethodElementType extends JavaStubElementType<PsiMethodStub, PsiMethod> {
  public JavaMethodElementType(@NonNls final String name) {
    super(name);
  }

  public PsiMethod createPsi(final PsiMethodStub stub) {
    if (isCompiled(stub)) {
      return new ClsMethodImpl(stub);
    }
    else {
      return stub.isAnnotationMethod() ? new PsiAnnotationMethodImpl(stub) : new PsiMethodImpl(stub);
    }
  }

  public PsiMethod createPsi(final ASTNode node) {
    if (node instanceof AnnotationMethodElement) {
      return new PsiAnnotationMethodImpl(node);
    }
    else {
      return new PsiMethodImpl(node);
    }
  }

  public PsiMethodStub createStub(final PsiMethod psi, final StubElement parentStub) {
    final byte flags = PsiMethodStubImpl.packFlags(psi.isConstructor(),
                                                   psi instanceof PsiAnnotationMethod,
                                                   psi.isVarArgs(),
                                                   RecordUtil.isDeprecatedByDocComment(psi),
                                                   RecordUtil.isDeprecatedByAnnotation(psi));

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

  public void serialize(final PsiMethodStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeName(stub.getName());
    RecordUtil.writeTYPE(dataStream, stub.getReturnTypeText());
    dataStream.writeByte(((PsiMethodStubImpl)stub).getFlags());
    if (stub.isAnnotationMethod()) {
      dataStream.writeName(stub.getDefaultValueText());
    }
  }

  public PsiMethodStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    StringRef name = dataStream.readName();
    final TypeInfo type = RecordUtil.readTYPE(dataStream);
    byte flags = dataStream.readByte();
    if (PsiMethodStubImpl.isAnnotationMethod(flags)) {
      final StringRef defaultMethodValue = dataStream.readName();
      return new PsiMethodStubImpl(parentStub, name, type, flags, defaultMethodValue);
    }
    return new PsiMethodStubImpl(parentStub, name, type, flags, null);
  }

  public void indexStub(final PsiMethodStub stub, final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(JavaMethodNameIndex.KEY, name);
    }
  }

  /*
  public String getId(final PsiMethodStub stub) {
    final String name = stub.getName();
    if (name != null) return name;
    return super.getId(stub);
  }
  */
}