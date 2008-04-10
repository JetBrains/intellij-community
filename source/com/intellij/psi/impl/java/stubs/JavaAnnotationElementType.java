/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.impl.java.stubs.impl.PsiAnnotationStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaAnnotationElementType extends JavaStubElementType<PsiAnnotationStub, PsiAnnotation> {
  public JavaAnnotationElementType() {
    super("ANNOTATION");
  }

  public PsiAnnotation createPsi(final PsiAnnotationStub stub) {
    throw new UnsupportedOperationException("createPsi is not implemented"); // TODO
  }

  public PsiAnnotationStub createStub(final PsiAnnotation psi, final StubElement parentStub) {
    return new PsiAnnotationStubImpl(parentStub, psi.getText());
  }

  public void serialize(final PsiAnnotationStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
    DataInputOutputUtil.writeNAME(dataStream, stub.getText(), nameStorage);
  }

  public PsiAnnotationStub deserialize(final DataInputStream dataStream, final StubElement parentStub, final PersistentStringEnumerator nameStorage)
      throws IOException {
    return new PsiAnnotationStubImpl(parentStub, DataInputOutputUtil.readNAME(dataStream, nameStorage));
  }

  public void indexStub(final PsiAnnotationStub stub, final IndexSink sink) {
    final String text = stub.getText();
    final String refText = getReferenceShortName(text);
    sink.occurrence(JavaAnnotationIndex.KEY, refText);
  }

  private static String getReferenceShortName(String annotationText) {
    final int index = annotationText.indexOf('('); //to get the text of reference itself
    if (index >= 0) {
      return PsiNameHelper.getShortClassName(annotationText.substring(0, index));
    }
    return PsiNameHelper.getShortClassName(annotationText);
  }
}