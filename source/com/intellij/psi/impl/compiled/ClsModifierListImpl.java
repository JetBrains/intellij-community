package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.cls.ClsUtil;
import com.intellij.util.containers.HashMap;

public class ClsModifierListImpl extends ClsElementImpl implements PsiModifierList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsModifierListImpl");

  private static final HashMap<String,Integer> ourModifierNameToFlagMap = new HashMap<String, Integer>();
  static{
    ourModifierNameToFlagMap.put(PsiModifier.PUBLIC, new Integer(ClsUtil.ACC_PUBLIC));
    ourModifierNameToFlagMap.put(PsiModifier.PROTECTED, new Integer(ClsUtil.ACC_PROTECTED));
    ourModifierNameToFlagMap.put(PsiModifier.PRIVATE, new Integer(ClsUtil.ACC_PRIVATE));
    ourModifierNameToFlagMap.put(PsiModifier.STATIC, new Integer(ClsUtil.ACC_STATIC));
    ourModifierNameToFlagMap.put(PsiModifier.ABSTRACT, new Integer(ClsUtil.ACC_ABSTRACT));
    ourModifierNameToFlagMap.put(PsiModifier.FINAL, new Integer(ClsUtil.ACC_FINAL));
    ourModifierNameToFlagMap.put(PsiModifier.NATIVE, new Integer(ClsUtil.ACC_NATIVE));
    ourModifierNameToFlagMap.put(PsiModifier.SYNCHRONIZED, new Integer(ClsUtil.ACC_SYNCHRONIZED));
    ourModifierNameToFlagMap.put(PsiModifier.TRANSIENT, new Integer(ClsUtil.ACC_TRANSIENT));
    ourModifierNameToFlagMap.put(PsiModifier.VOLATILE, new Integer(ClsUtil.ACC_VOLATILE));
  }

  private final ClsModifierListOwner myParent;
  private ClsAnnotationImpl[] myAnnotations = null;
  private final int myFlags;

  public ClsModifierListImpl(ClsModifierListOwner parent, int flags){
    myParent = parent;
    myFlags = flags;
  }

  public PsiElement[] getChildren(){
    return getAnnotations();
  }

  public PsiElement getParent(){
    return myParent;
  }

  public boolean hasModifierProperty(String name){
    Integer flag = ourModifierNameToFlagMap.get(name);
    if (flag == null){
      if (PsiModifier.PACKAGE_LOCAL.equals(name)){
        return (myFlags & (ClsUtil.ACC_PUBLIC | ClsUtil.ACC_PROTECTED | ClsUtil.ACC_PRIVATE)) == 0;
      }
      return false;
    }
    return (myFlags & flag.intValue()) != 0;
  }

  public void setModifierProperty(String name, boolean value) throws IncorrectOperationException{
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }
  
  public void checkSetModifierProperty(String name, boolean value) throws IncorrectOperationException{
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public PsiAnnotation[] getAnnotations() {
    if (myAnnotations == null) {
      myAnnotations = myParent.getAnnotations();
    }
    return myAnnotations;
  }

  public PsiAnnotation findAnnotation(String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  public String getMirrorText(){
    StringBuffer buffer = new StringBuffer();
    PsiAnnotation[] annotations = getAnnotations();
    for (int i = 0; i < annotations.length; i++) {
      PsiAnnotation annotation = annotations[i];
      buffer.append(((ClsAnnotationImpl)annotation).getMirrorText());
      buffer.append(" ");
    }

    //TODO : filtering & ordering modifiers can go to CodeStyleManager
    boolean isInterface = myParent instanceof PsiClass && ((PsiClass)myParent).isInterface();
    boolean isInterfaceMethod = myParent instanceof PsiMethod && ((PsiClass)myParent.getParent()).isInterface();
    boolean isInterfaceField = myParent instanceof PsiField && ((PsiClass)myParent.getParent()).isInterface();
    boolean isInterfaceClass = myParent instanceof PsiClass && myParent.getParent() instanceof PsiClass && ((PsiClass)myParent.getParent()).isInterface();
    if (hasModifierProperty(PsiModifier.PUBLIC)){
      if (!isInterfaceMethod && !isInterfaceField && !isInterfaceClass){
        buffer.append(PsiModifier.PUBLIC);
        buffer.append(' ');
      }
    }
    if (hasModifierProperty(PsiModifier.PROTECTED)){
      buffer.append(PsiModifier.PROTECTED);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.PRIVATE)){
      buffer.append(PsiModifier.PRIVATE);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.STATIC)){
      if (!isInterfaceField){
        buffer.append(PsiModifier.STATIC);
        buffer.append(' ');
      }
    }
    if (hasModifierProperty(PsiModifier.ABSTRACT)){
      if (!isInterface && !isInterfaceMethod){
        buffer.append(PsiModifier.ABSTRACT);
        buffer.append(' ');
      }
    }
    if (hasModifierProperty(PsiModifier.FINAL)){
      if (!isInterfaceField){
        buffer.append(PsiModifier.FINAL);
        buffer.append(' ');
      }
    }
    if (hasModifierProperty(PsiModifier.NATIVE)){
      buffer.append(PsiModifier.NATIVE);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.SYNCHRONIZED)){
      buffer.append(PsiModifier.SYNCHRONIZED);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.TRANSIENT)){
      buffer.append(PsiModifier.TRANSIENT);
      buffer.append(' ');
    }
    if (hasModifierProperty(PsiModifier.VOLATILE)){
      buffer.append(PsiModifier.VOLATILE);
      buffer.append(' ');
    }
    return buffer.toString();
  }

  public void setMirror(TreeElement element){
    LOG.assertTrue(myMirror == null);
    LOG.assertTrue(element.getElementType() == ElementType.MODIFIER_LIST);
    myMirror = element;
    PsiElement[] mirrorAnnotations = ((PsiModifierList)SourceTreeToPsiMap.treeElementToPsi(element)).getAnnotations();
    PsiAnnotation[] annotations = getAnnotations();
    LOG.assertTrue(annotations.length == mirrorAnnotations.length);
    for (int i = 0; i < annotations.length; i++) {
      ((ClsElementImpl)annotations[i]).setMirror(SourceTreeToPsiMap.psiElementToTree(mirrorAnnotations[i]));
    }
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitModifierList(this);
  }

  public String toString() {
    return "PsiModifierList";
  }
}
