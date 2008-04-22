/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.compiled.ClsParameterListImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiParameterListStubImpl;
import com.intellij.psi.impl.source.PsiParameterListImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaParameterListElementType extends JavaStubElementType<PsiParameterListStub, PsiParameterList> {
  public JavaParameterListElementType() {
    super("PARAMETER_LIST");
  }

  public PsiParameterList createPsi(final PsiParameterListStub stub) {
    if (isCompiled(stub)) {
      return new ClsParameterListImpl(stub);
    }
    else {
      return new PsiParameterListImpl(stub);
    }
  }

  public PsiParameterList createPsi(final ASTNode node) {
    return new PsiParameterListImpl(node);
  }

  public PsiParameterListStub createStub(final PsiParameterList psi, final StubElement parentStub) {
    return new PsiParameterListStubImpl(parentStub);
  }

  public void serialize(final PsiParameterListStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
  }

  public PsiParameterListStub deserialize(final DataInputStream dataStream, final StubElement parentStub, final PersistentStringEnumerator nameStorage)
      throws IOException {
    return new PsiParameterListStubImpl(parentStub);
  }

  public void indexStub(final PsiParameterListStub stub, final IndexSink sink) {
  }
}