/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.compiled.ClsParameterImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiParameterStubImpl;
import com.intellij.psi.impl.source.PsiParameterImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.StringRef;

import java.io.IOException;

public class JavaParameterElementType extends JavaStubElementType<PsiParameterStub, PsiParameter> {
  public JavaParameterElementType() {
    super("PARAMETER");
  }

  public PsiParameter createPsi(final PsiParameterStub stub) {
    if (isCompiled(stub)) {
      return new ClsParameterImpl(stub);
    }
    else {
      return new PsiParameterImpl(stub);
    }
  }

  public PsiParameter createPsi(final ASTNode node) {
    return new PsiParameterImpl(node);
  }

  public PsiParameterStub createStub(final PsiParameter psi, final StubElement parentStub) {
    final TypeInfo type = TypeInfo.create(psi.getType(), psi.getTypeElement());
    return new PsiParameterStubImpl(parentStub, psi.getName(), type, psi.isVarArgs());
  }

  public void serialize(final PsiParameterStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeName(stub.getName());
    RecordUtil.writeTYPE(dataStream, stub.getParameterType());
    dataStream.writeBoolean(stub.isParameterTypeEllipsis());
  }

  public PsiParameterStub deserialize(final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    StringRef name = dataStream.readName();
    TypeInfo type = RecordUtil.readTYPE(dataStream);
    boolean isEll = dataStream.readBoolean();
    return new PsiParameterStubImpl(parentStub, name, type, isEll);
  }

  public boolean shouldCreateStub(final ASTNode node) {
    final IElementType type = node.getTreeParent().getElementType();
    return type == JavaElementType.PARAMETER_LIST;
  }

  public void indexStub(final PsiParameterStub stub, final IndexSink sink) {
  }
}