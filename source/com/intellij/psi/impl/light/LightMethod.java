package com.intellij.psi.impl.light;

import com.intellij.pom.java.PomMethod;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.IncorrectOperationException;

import java.util.List;

/**
 * @author ven
 */
public class LightMethod extends LightElement implements PsiMethod {
  private PsiMethod myMethod;
  private PsiClass myContainingClass;

  public LightMethod(PsiManager manager, PsiMethod method, PsiClass containingClass) {
    super(manager);
    myMethod = method;
    myContainingClass = containingClass;
  }


  public boolean hasTypeParameters() {
    return myMethod.hasTypeParameters();
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

  public PsiElement setName(String name) throws IncorrectOperationException {
    return myMethod.setName(name);
  }

  public String getName() {
    return myMethod.getName();
  }

  public boolean hasModifierProperty(String name) {
    return myMethod.hasModifierProperty(name);
  }

  public PsiModifierList getModifierList() {
    return myMethod.getModifierList();
  }

  public PsiType getReturnType() {
    return myMethod.getReturnType();
  }

  public PsiTypeElement getReturnTypeElement() {
    return myMethod.getReturnTypeElement();
  }

  public PsiParameterList getParameterList() {
    return myMethod.getParameterList();
  }

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

  public MethodSignature getSignature(PsiSubstitutor substitutor) {
    return myMethod.getSignature(substitutor);
  }

  public PsiIdentifier getNameIdentifier() {
    return myMethod.getNameIdentifier();
  }

  public PsiMethod[] findSuperMethods() {
    return myMethod.findSuperMethods();
  }

  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return myMethod.findSuperMethods(checkAccess);
  }

  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return myMethod.findSuperMethods(parentClass);
  }

  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return myMethod.findSuperMethodSignaturesIncludingStatic(checkAccess);
  }

  public PsiMethod findConstructorInSuper() {
    return myMethod.findConstructorInSuper();
  }

  public PsiMethod findDeepestSuperMethod() {
    return myMethod.findDeepestSuperMethod();
  }

  public PomMethod getPom() {
    //TODO:
    return null;
  }

  public String getText() {
    return myMethod.getText();
  }

  public void accept(PsiElementVisitor visitor) {
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
}
