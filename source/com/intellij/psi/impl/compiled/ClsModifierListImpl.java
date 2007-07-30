package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.cls.ClsUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

public class ClsModifierListImpl extends ClsElementImpl implements PsiModifierList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsModifierListImpl");

  private static final TObjectIntHashMap<String> ourModifierNameToFlagMap = new TObjectIntHashMap<String>();

  static {
    ourModifierNameToFlagMap.put(PsiModifier.PUBLIC, ClsUtil.ACC_PUBLIC);
    ourModifierNameToFlagMap.put(PsiModifier.PROTECTED, ClsUtil.ACC_PROTECTED);
    ourModifierNameToFlagMap.put(PsiModifier.PRIVATE, ClsUtil.ACC_PRIVATE);
    ourModifierNameToFlagMap.put(PsiModifier.STATIC, ClsUtil.ACC_STATIC);
    ourModifierNameToFlagMap.put(PsiModifier.ABSTRACT, ClsUtil.ACC_ABSTRACT);
    ourModifierNameToFlagMap.put(PsiModifier.FINAL, ClsUtil.ACC_FINAL);
    ourModifierNameToFlagMap.put(PsiModifier.NATIVE, ClsUtil.ACC_NATIVE);
    ourModifierNameToFlagMap.put(PsiModifier.SYNCHRONIZED, ClsUtil.ACC_SYNCHRONIZED);
    ourModifierNameToFlagMap.put(PsiModifier.TRANSIENT, ClsUtil.ACC_TRANSIENT);
    ourModifierNameToFlagMap.put(PsiModifier.VOLATILE, ClsUtil.ACC_VOLATILE);
  }

  private final ClsModifierListOwner myParent;
  private ClsAnnotationImpl[] myAnnotations = null;
  private final int myFlags;

  public ClsModifierListImpl(ClsModifierListOwner parent, int flags) {
    myParent = parent;
    myFlags = flags;
  }

  @NotNull
  public PsiElement[] getChildren() {
    return getAnnotations();
  }

  public PsiElement getParent() {
    return myParent;
  }

  public boolean hasModifierProperty(@NotNull String name) {
    int flag = ourModifierNameToFlagMap.get(name);
    if (flag == 0) {
      return PsiModifier.PACKAGE_LOCAL.equals(name) &&
             (myFlags & (ClsUtil.ACC_PUBLIC | ClsUtil.ACC_PROTECTED | ClsUtil.ACC_PRIVATE)) == 0;
    }
    return (myFlags & flag) != 0;
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
    if (myAnnotations == null) {
      myAnnotations = myParent.getAnnotations();
    }
    return myAnnotations;
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

    //TODO : filtering & ordering modifiers can go to CodeStyleManager
    boolean isInterface = myParent instanceof PsiClass && ((PsiClass)myParent).isInterface();
    boolean isInterfaceMethod = myParent instanceof PsiMethod && myParent.getParent() instanceof PsiClass && ((PsiClass)myParent.getParent()).isInterface();
    boolean isInterfaceField = myParent instanceof PsiField && myParent.getParent() instanceof PsiClass && ((PsiClass)myParent.getParent()).isInterface();
    boolean isInterfaceClass = myParent instanceof PsiClass && myParent.getParent() instanceof PsiClass && ((PsiClass)myParent.getParent()).isInterface();
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
    LOG.assertTrue(myMirror == null);
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
    visitor.visitModifierList(this);
  }

  public String toString() {
    return "PsiModifierList";
  }
}
