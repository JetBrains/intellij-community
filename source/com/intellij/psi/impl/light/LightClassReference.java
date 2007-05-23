package com.intellij.psi.impl.light;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LightClassReference extends LightElement implements PsiJavaCodeReferenceElement {
  private final String myText;
  private final String myClassName;
  private final PsiElement myContext;
  private final GlobalSearchScope myResolveScope;
  private final PsiClass myRefClass;
  private PsiSubstitutor mySubstitutor;

  private LightReferenceParameterList myParameterList;

  private LightClassReference(PsiManager manager, @NonNls String text, @NonNls String className, PsiSubstitutor substitutor, GlobalSearchScope resolveScope) {
    super(manager);
    myText = text;
    myClassName = className;
    myResolveScope = resolveScope;

    myContext = null;
    myRefClass = null;
    mySubstitutor = substitutor;
  }

  public LightClassReference(PsiManager manager, @NonNls String text, @NonNls String className, GlobalSearchScope resolveScope) {
    this (manager, text, className, null, resolveScope);
  }

  public LightClassReference(PsiManager manager, @NonNls String text, @NonNls String className, PsiSubstitutor substitutor, PsiElement context) {
    super(manager);
    myText = text;
    myClassName = className;
    mySubstitutor = substitutor;
    myContext = context;

    myResolveScope = null;
    myRefClass = null;
  }

  public LightClassReference(PsiManager manager, @NonNls String text, PsiClass refClass) {
    this(manager, text, refClass, null);
  }

  public LightClassReference(PsiManager manager, @NonNls String text, PsiClass refClass, PsiSubstitutor substitutor) {
    super(manager);
    myText = text;
    myRefClass = refClass;

    myResolveScope = null;
    myClassName = null;
    myContext = null;
    mySubstitutor = substitutor;
  }

  public PsiElement resolve() {
    if (myClassName != null) {
      if (myContext != null) {
        return myManager.getResolveHelper().resolveReferencedClass(myClassName, myContext);
      }
      else {
        return myManager.findClass(myClassName, myResolveScope);
      }
    }
    else {
      return myRefClass;
    }
  }

  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode){
    final PsiElement resolved = resolve();
    PsiSubstitutor substitutor = mySubstitutor;
    if (substitutor == null) {
      if (resolved instanceof PsiClass) {
        substitutor = myManager.getElementFactory().createRawSubstitutor((PsiClass) resolved);
      } else {
        substitutor = PsiSubstitutor.EMPTY;
      }
    }
    return new CandidateInfo(resolved, substitutor);
  }

  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode){
    final JavaResolveResult result = advancedResolve(incompleteCode);
    if(result != JavaResolveResult.EMPTY) return new JavaResolveResult[]{result};
    return JavaResolveResult.EMPTY_ARRAY;
  }

  public void processVariants(PsiScopeProcessor processor){
    throw new RuntimeException("Variants are not available for light references");
  }

  public PsiElement getReferenceNameElement() {
    return null;
  }

  public PsiReferenceParameterList getParameterList() {
    if (myParameterList == null) {
      myParameterList = new LightReferenceParameterList(myManager, PsiTypeElement.EMPTY_ARRAY);
    }
    return myParameterList;
  }

  public String getQualifiedName() {
    if (myClassName != null) {
      PsiClass psiClass = (PsiClass)resolve();

      if (psiClass != null) {
        return psiClass.getQualifiedName();
      }
      else {
        return myClassName;
      }
    }
    else {
      return myRefClass.getQualifiedName();
    }
  }

  public String getReferenceName() {
    if (myClassName != null){
      return PsiNameHelper.getShortClassName(myClassName);
    }
    else{
      if (myRefClass instanceof PsiAnonymousClass){
        return ((PsiAnonymousClass)myRefClass).getBaseClassReference().getReferenceName();
      }
      else{
        return myRefClass.getName();
      }
    }
  }

  public String getText() {
    return myText;
  }

  public PsiReference getReference() {
    return this;
  }

  public String getCanonicalText() {
    String name = getQualifiedName();
    if (name == null) return null;
    PsiType[] types = getTypeParameters();
    if (types.length == 0) return name;

    StringBuffer buf = new StringBuffer();
    buf.append(name);
    buf.append('<');
    for (int i = 0; i < types.length; i++) {
      if (i > 0) buf.append(',');
      buf.append(types[i].getCanonicalText());
    }
    buf.append('>');

    return buf.toString();
  }

  public PsiElement copy() {
    if (myClassName != null) {
      if (myContext != null) {
        return new LightClassReference(myManager, myText, myClassName, mySubstitutor, myContext);
      }
      else{
        return new LightClassReference(myManager, myText, myClassName, mySubstitutor, myResolveScope);
      }
    }
    else {
      return new LightClassReference(myManager, myText, myRefClass, mySubstitutor);
    }
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    //TODO?
    throw new UnsupportedOperationException();
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    //TODO?
    throw new UnsupportedOperationException();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitReferenceElement(this);
  }

  public String toString() {
    return "LightClassReference:" + myText;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PsiClass)) return false;
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public Object[] getVariants() {
    throw new RuntimeException("Variants are not available for light references");
  }

  public boolean isSoft(){
    return false;
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  public PsiElement getElement() {
    return this;
  }

  public boolean isValid() {
    return myRefClass == null || myRefClass.isValid();
  }

  @NotNull
  public PsiType[] getTypeParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  public PsiElement getQualifier() {
    return null;
  }

  public boolean isQualified() {
    return false;
  }
}
