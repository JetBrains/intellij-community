package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class ClsArrayInitializerMemberValueImpl extends ClsElementImpl implements PsiArrayInitializerMemberValue {
  private final ClsElementImpl myParent;
  private final PsiAnnotationMemberValue[] myInitializers;
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsArrayInitializerMemberValueImpl");

  public ClsArrayInitializerMemberValueImpl(ClsElementImpl parent, PsiAnnotationMemberValue[] initializers) {
    myParent = parent;
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
    setMirrorCheckingType(element, null);

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
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotationArrayInitializer(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NotNull
  public PsiAnnotationMemberValue[] getInitializers() {
    return myInitializers;
  }
}
