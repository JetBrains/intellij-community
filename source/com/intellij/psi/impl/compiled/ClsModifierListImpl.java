package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.impl.source.PsiModifierListImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ClsModifierListImpl extends ClsRepositoryPsiElement<PsiModifierListStub> implements PsiModifierList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsModifierListImpl");

  public ClsModifierListImpl(final PsiModifierListStub stub) {
    super(stub);
  }

  @NotNull
  public PsiElement[] getChildren() {
    return getAnnotations();
  }

  public boolean hasModifierProperty(@NotNull String name) {
    int flag = PsiModifierListImpl.NAME_TO_MODIFIER_FLAG_MAP.get(name);
    assert flag != 0;
    return (getStub().getModifiersMask() & flag) != 0;
  }

  public boolean hasExplicitModifier(@NotNull String name) {
    return hasModifierProperty(name);
  }

  public void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public void checkSetModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return getStub().getChildrenByType(JavaStubElementTypes.ANNOTATION, PsiAnnotation.EMPTY_ARRAY);
  }

  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    PsiAnnotation[] annotations = getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      ((ClsAnnotationImpl)annotation).appendMirrorText(indentLevel, buffer);
      buffer.append(' ');
    }

    PsiElement parent = getParent();

    //TODO : filtering & ordering modifiers can go to CodeStyleManager
    boolean isInterface = parent instanceof PsiClass && ((PsiClass)parent).isInterface();
    boolean isInterfaceMethod = parent instanceof PsiMethod && parent.getParent() instanceof PsiClass && ((PsiClass)parent.getParent()).isInterface();
    boolean isInterfaceField = parent instanceof PsiField && parent.getParent() instanceof PsiClass && ((PsiClass)parent.getParent()).isInterface();
    boolean isInterfaceClass = parent instanceof PsiClass && parent.getParent() instanceof PsiClass && ((PsiClass)parent.getParent()).isInterface();
    if (hasModifierProperty(PsiModifier.PUBLIC)) {
      if (!isInterfaceMethod && !isInterfaceField && !isInterfaceClass) {
        buffer.append(PsiModifier.PUBLIC);
        buffer.append(' ');
      }
    }
    if (hasModifierProperty(PsiModifier.PROTECTED)) {
      buffer.append(PsiModifier.PROTECTED);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.PRIVATE)) {
      buffer.append(PsiModifier.PRIVATE);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.STATIC)) {
      if (!isInterfaceField) {
        buffer.append(PsiModifier.STATIC);
        buffer.append(' ');
      }
    }
    if (hasModifierProperty(PsiModifier.ABSTRACT)) {
      if (!isInterface && !isInterfaceMethod) {
        buffer.append(PsiModifier.ABSTRACT);
        buffer.append(' ');
      }
    }
    if (hasModifierProperty(PsiModifier.FINAL)) {
      if (!isInterfaceField) {
        buffer.append(PsiModifier.FINAL);
        buffer.append(' ');
      }
    }
    if (hasModifierProperty(PsiModifier.NATIVE)) {
      buffer.append(PsiModifier.NATIVE);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      buffer.append(PsiModifier.SYNCHRONIZED);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.TRANSIENT)) {
      buffer.append(PsiModifier.TRANSIENT);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.VOLATILE)) {
      buffer.append(PsiModifier.VOLATILE);
      buffer.append(' ');
    }
  }

  public void setMirror(@NotNull TreeElement element) {
    LOG.assertTrue(!CHECK_MIRROR_ENABLED || myMirror == null);
    LOG.assertTrue(element.getElementType() == JavaElementType.MODIFIER_LIST);
    myMirror = element;
    PsiElement[] mirrorAnnotations = ((PsiModifierList)SourceTreeToPsiMap.treeElementToPsi(element)).getAnnotations();
    PsiAnnotation[] annotations = getAnnotations();
    LOG.assertTrue(annotations.length == mirrorAnnotations.length);
    for (int i = 0; i < annotations.length; i++) {
        ((ClsElementImpl)annotations[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirrorAnnotations[i]));
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitModifierList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiModifierList";
  }
}
