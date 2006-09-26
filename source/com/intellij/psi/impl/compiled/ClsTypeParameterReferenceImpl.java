package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ClsTypeParameterReferenceImpl extends ClsElementImpl implements PsiJavaCodeReferenceElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsTypeParameterReferenceImpl");
  private final PsiElement myParent;
  private final String myName;

  public ClsTypeParameterReferenceImpl(PsiElement parent, String name) {
    myParent = parent;
    myName = name;
  }

  public void processVariants(PsiScopeProcessor processor) {
    throw new RuntimeException("Variants are not available for light references");
  }

  public PsiElement getReferenceNameElement() {
    return null;
  }

  public PsiReferenceParameterList getParameterList() {
    return null;
  }

  public String getQualifiedName() {
    return myName;
  }

  public String getReferenceName() {
    return myName;
  }

  public PsiElement resolve() {
    LOG.assertTrue(myParent.isValid());
    PsiElement parent = myParent;
    while (!(parent instanceof PsiFile)) {
      PsiTypeParameterList parameterList = null;
      if (parent instanceof PsiClass) {
        parameterList = ((PsiClass) parent).getTypeParameterList();
      }
      else if (parent instanceof PsiMethod) {
        parameterList = ((PsiMethod) parent).getTypeParameterList();
      }

      if (parameterList != null) {
        PsiTypeParameter[] parameters = parameterList.getTypeParameters();
        for (PsiTypeParameter parameter : parameters) {
          if (myName.equals(parameter.getName())) return parameter;
        }
      }
      parent = parent.getParent();
    }

    return null;
  }

  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode){
    return new CandidateInfo(resolve(), PsiSubstitutor.EMPTY);
  }

  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode){
    final JavaResolveResult result = advancedResolve(incompleteCode);
    if(result != JavaResolveResult.EMPTY) return new JavaResolveResult[]{result};
    return JavaResolveResult.EMPTY_ARRAY;
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

  public String getCanonicalText() {
    return myName;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof ClsTypeParameterImpl)) return false;

    return element == resolve();
  }

  public String getText() {
    return myName;
  }

  public int getTextLength() {
    return getText().length();
  }

  public PsiReference getReference() {
    return this;
  }

  @NotNull
  public PsiElement[] getChildren(){
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiElement getParent(){
    return myParent;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public Object[] getVariants() {
    throw new RuntimeException("Variants are not available for references to compiled code");
  }

  public boolean isSoft(){
    return false;
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer){
    buffer.append(getCanonicalText());
  }

  public void setMirror(TreeElement element){
    LOG.assertTrue(myMirror == null);
    LOG.assertTrue(element.getElementType() == ElementType.JAVA_CODE_REFERENCE);
    myMirror = element;
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitReferenceElement(this);
  }

  public String toString() {
    return "PsiJavaCodeReferenceElement:" + getText();
  }

  public TextRange getRangeInElement() {
    getMirror();
    return myMirror != null ? myMirror.getTextRange() : new TextRange(0, getTextLength());
  }

  public PsiElement getElement() {
    return this;
  }

}
