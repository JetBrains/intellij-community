/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.compiled.ClsModifierListImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiModifierListStubImpl;
import com.intellij.psi.impl.source.PsiModifierListImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaModifierListElementType extends JavaStubElementType<PsiModifierListStub, PsiModifierList> {
  public JavaModifierListElementType() {
    super("MODIFIER_LIST");
  }

  public PsiModifierList createPsi(final PsiModifierListStub stub) {
    if (isCompiled(stub)) {
      return new ClsModifierListImpl(stub);
    }
    else {
      return new PsiModifierListImpl(stub);
    }
  }

  public PsiModifierList createPsi(final ASTNode node) {
    return new PsiModifierListImpl(node);
  }

  public PsiModifierListStub createStub(final PsiModifierList psi, final StubElement parentStub) {
    return new PsiModifierListStubImpl(parentStub, RecordUtil.packModifierList(psi));
  }

  public void serialize(final PsiModifierListStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
    DataInputOutputUtil.writeINT(dataStream, stub.getModifiersMask());
  }

  public PsiModifierListStub deserialize(final DataInputStream dataStream, final StubElement parentStub, final PersistentStringEnumerator nameStorage)
      throws IOException {
    return new PsiModifierListStubImpl(parentStub, DataInputOutputUtil.readINT(dataStream));
  }

  public void indexStub(final PsiModifierListStub stub, final IndexSink sink) {
  }
}