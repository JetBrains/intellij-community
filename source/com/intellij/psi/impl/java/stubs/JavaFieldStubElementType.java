/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.impl.cache.InitializerTooLongException;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.compiled.ClsEnumConstantImpl;
import com.intellij.psi.impl.compiled.ClsFieldImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiFieldStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaFieldNameIndex;
import com.intellij.psi.impl.source.PsiEnumConstantImpl;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.impl.source.tree.java.EnumConstantElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class JavaFieldStubElementType extends JavaStubElementType<PsiFieldStub, PsiField> {
  public JavaFieldStubElementType(@NotNull @NonNls final String id) {
    super(id);
  }

  public PsiField createPsi(final PsiFieldStub stub) {
    final boolean compiled = isCompiled(stub);
    if (compiled) {
      return stub.isEnumConstant() ? new ClsEnumConstantImpl(stub) : new ClsFieldImpl(stub);
    }
    else {
      return stub.isEnumConstant() ? new PsiEnumConstantImpl(stub) : new PsiFieldImpl(stub);
    }
  }

  public PsiField createPsi(final ASTNode node) {
    if (node instanceof EnumConstantElement) {
      return new PsiEnumConstantImpl(node);
    }
    else {
      return new PsiFieldImpl(node);
    }
  }

  public PsiFieldStub createStub(final PsiField psi, final StubElement parentStub) {
    final PsiExpression initializer = psi.getInitializer();
    final TypeInfo type = TypeInfo.create(psi.getType(), psi.getTypeElement());
    final byte flags = PsiFieldStubImpl.packFlags(psi instanceof PsiEnumConstant,
                                                  RecordUtil.isDeprecatedByDocComment(psi),
                                                  RecordUtil.isDeprecatedByAnnotation(psi));
    return new PsiFieldStubImpl(parentStub, psi.getName(), type, initializer != null ? initializer.getText() : null, flags);
  }

  public void serialize(final PsiFieldStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeName(stub.getName());
    RecordUtil.writeTYPE(dataStream, stub.getType());
    dataStream.writeName(getInitializerText(stub));
    dataStream.writeByte(((PsiFieldStubImpl)stub).getFlags());
  }

  private static String getInitializerText(final PsiFieldStub stub) {
    try {
      return stub.getInitializerText();
    }
    catch (InitializerTooLongException e) {
      return StringRef.toString(PsiFieldStubImpl.INITIALIZER_TOO_LONG);
    }
  }

  public PsiFieldStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    StringRef name = dataStream.readName();

    final TypeInfo type = RecordUtil.readTYPE(dataStream);

    StringRef initializerText = dataStream.readName();
    byte flags = dataStream.readByte();
    return new PsiFieldStubImpl(parentStub, name, type, initializerText, flags);
  }

  public void indexStub(final PsiFieldStub stub, final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(JavaFieldNameIndex.KEY, name);
    }
  }

  public String getId(final PsiFieldStub stub) {
    final String name = stub.getName();
    if (name != null) return name;

    return super.getId(stub);
  }
}