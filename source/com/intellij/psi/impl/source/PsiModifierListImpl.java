package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class PsiModifierListImpl extends JavaStubPsiElement<PsiModifierListStub> implements PsiModifierList, Constants {
  private static final Map<String, IElementType> NAME_TO_KEYWORD_TYPE_MAP = new THashMap<String, IElementType>();

  static{
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.PUBLIC, PUBLIC_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.PROTECTED, PROTECTED_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.PRIVATE, PRIVATE_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.STATIC, STATIC_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.ABSTRACT, ABSTRACT_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.FINAL, FINAL_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.NATIVE, NATIVE_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.SYNCHRONIZED, SYNCHRONIZED_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.STRICTFP, STRICTFP_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.TRANSIENT, TRANSIENT_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.VOLATILE, VOLATILE_KEYWORD);
  }

  public static final TObjectIntHashMap<String> NAME_TO_MODIFIER_FLAG_MAP = new TObjectIntHashMap<String>();

  static{
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PUBLIC, ModifierFlags.PUBLIC_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PROTECTED, ModifierFlags.PROTECTED_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PRIVATE, ModifierFlags.PRIVATE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PACKAGE_LOCAL, ModifierFlags.PACKAGE_LOCAL_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.STATIC, ModifierFlags.STATIC_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.ABSTRACT, ModifierFlags.ABSTRACT_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.FINAL, ModifierFlags.FINAL_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.NATIVE, ModifierFlags.NATIVE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.SYNCHRONIZED, ModifierFlags.SYNCHRONIZED_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.STRICTFP, ModifierFlags.STRICTFP_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.TRANSIENT, ModifierFlags.TRANSIENT_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.VOLATILE, ModifierFlags.VOLATILE_MASK);
  }

  public PsiModifierListImpl(final PsiModifierListStub stub) {
    super(stub, JavaStubElementTypes.MODIFIER_LIST);
  }

  public PsiModifierListImpl(final ASTNode node) {
    super(node);
  }

  public boolean hasModifierProperty(@NotNull String name){
    final PsiModifierListStub stub = getStub();
    if (stub != null) {
      int flag = NAME_TO_MODIFIER_FLAG_MAP.get(name);
      assert flag != 0;
      return (stub.getModifiersMask() & flag) != 0;
    }

    IElementType type = NAME_TO_KEYWORD_TYPE_MAP.get(name);

    PsiElement parent = getParent();
    if (parent instanceof PsiClass){
      PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiClass && ((PsiClass)pparent).isInterface()){
        if (type == PUBLIC_KEYWORD){
          return true;
        }
        if (type == null){ // package local
          return false;
        }
        if (type == STATIC_KEYWORD){
          return true;
        }
      }
      if (((PsiClass)parent).isInterface()){
        if (type == ABSTRACT_KEYWORD){
          return true;
        }

        // nested interface is implicitly static
        if (pparent instanceof PsiClass) {
          if (type == STATIC_KEYWORD){
            return true;
          }
        }
      }
      if (((PsiClass)parent).isEnum()){
        if (type == STATIC_KEYWORD) {
          if (!(pparent instanceof PsiFile)) return true;
        }
        else if (type == FINAL_KEYWORD) {
          final PsiField[] fields = ((PsiClass)parent).getFields();
          for (PsiField field : fields) {
            if (field instanceof PsiEnumConstant && ((PsiEnumConstant)field).getInitializingClass() != null) return false;
          }
          return true;
        }
        else if (type == ABSTRACT_KEYWORD) {
          final PsiMethod[] methods = ((PsiClass)parent).getMethods();
          for (PsiMethod method : methods) {
            if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return true;
          }
          return false;
        }
      }
    }
    else if (parent instanceof PsiMethod){
      PsiClass aClass = ((PsiMethod)parent).getContainingClass();
      if (aClass != null && aClass.isInterface()){
        if (type == PUBLIC_KEYWORD){
          return true;
        }
        if (type == null){ // package local
          return false;
        }
        if (type == ABSTRACT_KEYWORD){
          return true;
        }
      }
    }
    else if (parent instanceof PsiField){
      if (parent instanceof PsiEnumConstant) {
        return type == PUBLIC_KEYWORD || type == STATIC_KEYWORD || type == FINAL_KEYWORD;
      }
      else {
        PsiClass aClass = ((PsiField)parent).getContainingClass();
        if (aClass != null && aClass.isInterface()){
          if (type == PUBLIC_KEYWORD){
            return true;
          }
          if (type == null){ // package local
            return false;
          }
          if (type == STATIC_KEYWORD){
            return true;
          }
          if (type == FINAL_KEYWORD){
            return true;
          }
        }
      }
    }

    if (type == null){ // package local
      return !hasModifierProperty(PsiModifier.PUBLIC) && !hasModifierProperty(PsiModifier.PRIVATE) && !hasModifierProperty(PsiModifier.PROTECTED);
    }

    return TreeUtil.findChild(getNode(), type) != null;
  }

  public boolean hasExplicitModifier(@NotNull String name) {
    final CompositeElement tree = (CompositeElement)getNode();
    IElementType type = NAME_TO_KEYWORD_TYPE_MAP.get(name);
    return TreeUtil.findChild(tree, type) != null;
  }

  public void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException{
    checkSetModifierProperty(name, value);

    IElementType type = NAME_TO_KEYWORD_TYPE_MAP.get(name);

    CompositeElement treeElement = (CompositeElement)getNode();
    ASTNode parentTreeElement = treeElement.getTreeParent();
    if (value){
      if (parentTreeElement.getElementType() == FIELD &&
          parentTreeElement.getTreeParent().getElementType() == CLASS &&
          ((PsiClass)SourceTreeToPsiMap.treeElementToPsi(parentTreeElement.getTreeParent())).isInterface()) {
        if (type == PUBLIC_KEYWORD || type == STATIC_KEYWORD || type == FINAL_KEYWORD) return;
      }
      else if (parentTreeElement.getElementType() == METHOD &&
               parentTreeElement.getTreeParent().getElementType() == CLASS &&
               ((PsiClass)SourceTreeToPsiMap.treeElementToPsi(parentTreeElement.getTreeParent())).isInterface()) {
        if (type == PUBLIC_KEYWORD || type == ABSTRACT_KEYWORD) return;
      }
      else if (parentTreeElement.getElementType() == CLASS &&
               parentTreeElement.getTreeParent().getElementType() == CLASS &&
               ((PsiClass)SourceTreeToPsiMap.treeElementToPsi(parentTreeElement.getTreeParent())).isInterface()) {
        if (type == PUBLIC_KEYWORD) return;
      }

      if (type == PUBLIC_KEYWORD
          || type == PRIVATE_KEYWORD
          || type == PROTECTED_KEYWORD
          || type == null /* package local */){

        if (type != PUBLIC_KEYWORD){
          setModifierProperty(PsiModifier.PUBLIC, false);
        }
        if (type != PRIVATE_KEYWORD){
          setModifierProperty(PsiModifier.PRIVATE, false);
        }
        if (type != PROTECTED_KEYWORD){
          setModifierProperty(PsiModifier.PROTECTED, false);
        }
        if (type == null) return;
      }

      if (TreeUtil.findChild(treeElement, type) == null){
        TreeElement keyword = Factory.createSingleLeafElement(type, name, 0, name.length(), null, getManager());
        treeElement.addInternal(keyword, keyword, null, null);
      }
      if ((type == ABSTRACT_KEYWORD || type == NATIVE_KEYWORD) && parentTreeElement.getElementType() == METHOD){
        //Q: remove body?
      }
    }
    else{
      if (type == null){ // package local
        throw new IncorrectOperationException("Cannot reset package local modifier."); //?
      }
      ASTNode child = TreeUtil.findChild(treeElement, type);
      if (child != null){
        SourceTreeToPsiMap.treeElementToPsi(child).delete();
      }
    }
  }

  public void checkSetModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException{
    CheckUtil.checkWritable(this);
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return getStubOrPsiChildren(JavaStubElementTypes.ANNOTATION, PsiAnnotation.EMPTY_ARRAY);
  }

  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitModifierList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiModifierList:" + getText();
  }
}