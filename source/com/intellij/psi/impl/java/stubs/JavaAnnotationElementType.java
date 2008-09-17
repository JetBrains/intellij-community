/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.impl.compiled.ClsAnnotationImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiAnnotationStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;

import java.io.IOException;

public class JavaAnnotationElementType extends JavaStubElementType<PsiAnnotationStub, PsiAnnotation> {
  public JavaAnnotationElementType() {
    super("ANNOTATION");
  }

  public PsiAnnotation createPsi(final PsiAnnotationStub stub) {
    if (isCompiled(stub)) {
      return new ClsAnnotationImpl(stub);
    }
    else {
      return new PsiAnnotationImpl(stub);
    }
  }

  public PsiAnnotation createPsi(final ASTNode node) {
    return new PsiAnnotationImpl(node);
  }  

  public PsiAnnotationStub createStub(final PsiAnnotation psi, final StubElement parentStub) {
    return new PsiAnnotationStubImpl(parentStub, psi.getText());
  }

  public void serialize(final PsiAnnotationStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeUTFFast(stub.getText());
  }

  public PsiAnnotationStub deserialize(final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    return new PsiAnnotationStubImpl(parentStub, dataStream.readUTFFast());
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