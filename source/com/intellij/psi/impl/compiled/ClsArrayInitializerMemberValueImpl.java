package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class ClsArrayInitializerMemberValueImpl extends ClsElementImpl implements PsiArrayInitializerMemberValue {
  private final ClsElementImpl myParent;
  private PsiAnnotationMemberValue[] myInitializers;
  private final static Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsArrayInitializerMemberValueImpl");

  public ClsArrayInitializerMemberValueImpl(ClsElementImpl parent) {
    myParent = parent;
  }

  public void setInitializers(PsiAnnotationMemberValue[] initializers) {
    myInitializers = initializers;
  }

  public String getText() {
    StringBuffer buffer = new StringBuffer();
    appendMirrorText(0, buffer);
    return buffer.toString();
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    buffer.append('{');
    for (int i = 0; i < myInitializers.length; i++) {
      if (i > 0) buffer.append(", ");
      ((ClsElementImpl)myInitializers[i]).appendMirrorText(0, buffer);
    }
    buffer.append('}');
  }

  public void setMirror(@NotNull TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(!CHECK_MIRROR_ENABLED || myMirror == null);
    myMirror = element;

    PsiArrayInitializerMemberValue mirror = (PsiArrayInitializerMemberValue)SourceTreeToPsiMap.treeElementToPsi(element);
    PsiAnnotationMemberValue[] initializers = mirror.getInitializers();
    LOG.assertTrue(myInitializers.length == initializers.length);
    for (int i = 0; i < myInitializers.length; i++) {
      ClsElementImpl value = (ClsElementImpl)myInitializers[i];
      value.setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(initializers[i]));
    }
  }

  @NotNull
  public PsiElement[] getChildren() {
    return myInitializers;
  }

  public PsiElement getParent() {
    return myParent;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitAnnotationArrayInitializer(this);
  }

  @NotNull
  public PsiAnnotationMemberValue[] getInitializers() {
    return myInitializers;
  }
}
