package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.ui.RowIcon;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author ven
 */
public class LightMethod extends LightElement implements PsiMethod {
  private PsiMethod myMethod;
  private PsiClass myContainingClass;

  public LightMethod(PsiManager manager, PsiMethod method, PsiClass containingClass) {
    super(manager, StdFileTypes.JAVA.getLanguage());
    myMethod = method;
    myContainingClass = containingClass;
  }


  public boolean hasTypeParameters() {
    return myMethod.hasTypeParameters();
  }

  @NotNull public PsiTypeParameter[] getTypeParameters() {
    return myMethod.getTypeParameters();
  }

  public PsiTypeParameterList getTypeParameterList() {
    return myMethod.getTypeParameterList();
  }

  public PsiDocComment getDocComment() {
    return myMethod.getDocComment();
  }

  public boolean isDeprecated() {
    return myMethod.isDeprecated();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    return myMethod.setName(name);
  }

  @NotNull
  public String getName() {
    return myMethod.getName();
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return myMethod.getHierarchicalMethodSignature();
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return myMethod.hasModifierProperty(name);
  }

  @NotNull
  public PsiModifierList getModifierList() {
    return myMethod.getModifierList();
  }

  public PsiType getReturnType() {
    return myMethod.getReturnType();
  }

  public PsiTypeElement getReturnTypeElement() {
    return myMethod.getReturnTypeElement();
  }

  @NotNull
  public PsiParameterList getParameterList() {
    return myMethod.getParameterList();
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    return myMethod.getThrowsList();
  }

  public PsiCodeBlock getBody() {
    return myMethod.getBody();
  }

  public boolean isConstructor() {
    return myMethod.isConstructor();
  }

  public boolean isVarArgs() {
    return myMethod.isVarArgs();
  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return myMethod.getSignature(substitutor);
  }

  public PsiIdentifier getNameIdentifier() {
    return myMethod.getNameIdentifier();
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    return myMethod.findSuperMethods();
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return myMethod.findSuperMethods(checkAccess);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return myMethod.findSuperMethods(parentClass);
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return myMethod.findSuperMethodSignaturesIncludingStatic(checkAccess);
  }

  public PsiMethod findDeepestSuperMethod() {
    return myMethod.findDeepestSuperMethod();
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return myMethod.findDeepestSuperMethods();
  }

  public String getText() {
    return myMethod.getText();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    myMethod.accept(visitor);
  }

  public PsiElement copy() {
    return new LightMethod(myManager, (PsiMethod)myMethod.copy(), myContainingClass);
  }

  public boolean isValid() {
    return myContainingClass.isValid();
  }

  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  public String toString() {
    return "PsiMethod:" + getName();
  }

  public Icon getElementIcon(final int flags) {
    Icon methodIcon = hasModifierProperty(PsiModifier.ABSTRACT) ? Icons.ABSTRACT_METHOD_ICON : Icons.METHOD_ICON;
    RowIcon baseIcon = createLayeredIcon(methodIcon, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }

  @NotNull
  public SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }

  @Override
  public PsiElement getContext() {
    return getContainingClass();
  }
}
