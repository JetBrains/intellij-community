package com.intellij.psi.impl.light;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;

public class LightClassReference extends LightElement implements PsiJavaCodeReferenceElement {
  private final String myText;
  private final String myClassName;
  private final PsiElement myContext;
  private final GlobalSearchScope myResolveScope;
  private final PsiClass myRefClass;

  private LightReferenceParameterList myParameterList;

  public LightClassReference(PsiManager manager, String text, String className, GlobalSearchScope resolveScope) {
    super(manager);
    myText = text;
    myClassName = className;
    myResolveScope = resolveScope;

    myContext = null;
    myRefClass = null;
  }

  public LightClassReference(PsiManager manager, String text, String className, PsiElement context) {
    super(manager);
    myText = text;
    myClassName = className;
    myContext = context;

    myResolveScope = null;
    myRefClass = null;
  }

  public LightClassReference(PsiManager manager, String text, PsiClass refClass) {
    super(manager);
    myText = text;
    myRefClass = refClass;

    myResolveScope = null;
    myClassName = null;
    myContext = null;
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

  public ResolveResult advancedResolve(boolean incompleteCode){
    final PsiElement resolved = resolve();
    final PsiSubstitutor rawSubstitutor;
    if (resolved instanceof PsiClass) {
      rawSubstitutor = myManager.getElementFactory().createRawSubstitutor((PsiClass) resolved);
    } else {
      rawSubstitutor = PsiSubstitutor.EMPTY;
    }
    return new CandidateInfo(resolved, rawSubstitutor);
  }

  public ResolveResult[] multiResolve(boolean incompleteCode){
    final ResolveResult result = advancedResolve(incompleteCode);
    if(result != ResolveResult.EMPTY) return new ResolveResult[]{result};
    return ResolveResult.EMPTY_ARRAY;
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
        return new LightClassReference(myManager, myText, myClassName, myContext);
      }
      else{
        return new LightClassReference(myManager, myText, myClassName, myResolveScope);
      }
    }
    else {
      return new LightClassReference(myManager, myText, myRefClass);
    }
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    //TODO?
    throw new UnsupportedOperationException();
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    //TODO?
    throw new UnsupportedOperationException();
  }

  public void accept(PsiElementVisitor visitor) {
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
