/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.compiled.ClsTypeParametersListImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiTypeParameterListStubImpl;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterListImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaTypeParameterListElementType extends JavaStubElementType<PsiTypeParameterListStub, PsiTypeParameterList> {
  public JavaTypeParameterListElementType() {
    super("TYPE_PARAMETER_LIST");
  }

  public PsiTypeParameterList createPsi(final PsiTypeParameterListStub stub) {
    if (isCompiled(stub)) {
      return new ClsTypeParametersListImpl(stub);
    }
    else {
      return new PsiTypeParameterListImpl(stub);
    }
  }

  public PsiTypeParameterList createPsi(final ASTNode node) {
    return new PsiTypeParameterListImpl(node);
  }

  public PsiTypeParameterListStub createStub(final PsiTypeParameterList psi, final StubElement parentStub) {
    return new PsiTypeParameterListStubImpl(parentStub);
  }

  public void serialize(final PsiTypeParameterListStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
  }

  public PsiTypeParameterListStub deserialize(final DataInputStream dataStream, final StubElement parentStub, final PersistentStringEnumerator nameStorage)
      throws IOException {
    return new PsiTypeParameterListStubImpl(parentStub);
  }

  public void indexStub(final PsiTypeParameterListStub stub, final IndexSink sink) {
  }
}