package com.intellij.psi.impl.light;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class LightMemberReference extends LightElement implements PsiJavaCodeReferenceElement {
  private final PsiMember myRefMember;
  private PsiSubstitutor mySubstitutor;

  private LightReferenceParameterList myParameterList;

  public LightMemberReference(PsiManager manager, PsiMember refClass, PsiSubstitutor substitutor) {
    super(manager);
    myRefMember = refClass;

    mySubstitutor = substitutor;
  }

  public PsiElement resolve() {
      return myRefMember;
  }

  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode){
    final PsiElement resolved = resolve();
    PsiSubstitutor substitutor = mySubstitutor;
    if (substitutor == null) {
        substitutor = PsiSubstitutor.EMPTY;
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
    final PsiClass containingClass = myRefMember.getContainingClass();
    if (containingClass != null) {
      final String qualifiedName = containingClass.getQualifiedName();
      if (qualifiedName != null) {
        return qualifiedName + '.' + myRefMember.getName();
      }
    }
    return myRefMember.getName();
  }

  public String getReferenceName() {
    return getQualifiedName();
  }

  public String getText() {
    return myRefMember.getName() + getParameterList().getText();
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
      return new LightMemberReference(myManager, myRefMember, mySubstitutor);
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    //TODO?
    throw new IncorrectOperationException();
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    //TODO?
    throw new IncorrectOperationException();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitReferenceElement(this);
  }

  public String toString() {
    return "LightClassReference:" + myRefMember.getName();
  }

  public boolean isReferenceTo(PsiElement element) {
    return element instanceof PsiClass && element.getManager().areElementsEquivalent(element, resolve());
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
    PsiReferenceParameterList parameterList = getParameterList();
    if (parameterList != null && !parameterList.isValid()) return false;
    return myRefMember == null || myRefMember.isValid();
  }

  @NotNull
  public PsiType[] getTypeParameters() {
    PsiReferenceParameterList parameterList = getParameterList();
    return parameterList == null ? PsiType.EMPTY_ARRAY : parameterList.getTypeArguments();
  }

  public PsiElement getQualifier() {
    return null;
  }

  public boolean isQualified() {
    return false;
  }
}
