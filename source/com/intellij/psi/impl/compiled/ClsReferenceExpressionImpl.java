package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;

public class ClsReferenceExpressionImpl extends ClsElementImpl implements PsiReferenceExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsReferenceExpressionImpl");

  private final ClsElementImpl myParent;
  private final PsiReferenceExpression myPatternExpression;
  private final PsiReferenceExpression myQualifier;
  private final String myName;
  private final PsiIdentifier myNameElement;

  public ClsReferenceExpressionImpl(ClsElementImpl parent, PsiReferenceExpression patternExpression) {
    myParent = parent;
    myPatternExpression = patternExpression;

    PsiReferenceExpression patternQualifier = (PsiReferenceExpression)myPatternExpression.getQualifierExpression();
    if (patternQualifier != null){
      myQualifier = new ClsReferenceExpressionImpl(this, patternQualifier);
    }
    else{
      myQualifier = null;
    }

    myName = myPatternExpression.getReferenceName();
    myNameElement = new ClsIdentifierImpl(this, myName);
  }

  public PsiElement getParent() {
    return myParent;
  }

  public PsiExpression getQualifierExpression() {
    return myQualifier;
  }

  public PsiElement bindToElementViaStaticImport(PsiClass aClass) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement getReferenceNameElement() {
    return myNameElement;
  }

  public PsiReferenceParameterList getParameterList() {
    return null;
  }

  public PsiElement[] getChildren() {
    if (myQualifier != null){
      return new PsiElement[]{myQualifier, myNameElement};
    }
    else{
      return new PsiElement[]{myNameElement};
    }
  }

  public String getText() {
    return myQualifier != null ? myQualifier.getText() + "." + myName : myName;
  }

  public boolean isQualified() {
    return myQualifier != null;
  }

  public PsiType getType() {
    return myPatternExpression.getType();
  }

  public PsiElement resolve() {
    return myPatternExpression.resolve();
  }

  public ResolveResult advancedResolve(boolean incompleteCode){
    return myPatternExpression.advancedResolve(incompleteCode);
  }

  public ResolveResult[] multiResolve(boolean incompleteCode){
    final ResolveResult result = advancedResolve(incompleteCode);
    if(result != ResolveResult.EMPTY) return new ResolveResult[]{result};
    return ResolveResult.EMPTY_ARRAY;
  }


  public PsiElement getElement() {
    return this;
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  public String getCanonicalText() {
    return myPatternExpression.getCanonicalText();
  }

  public String getQualifiedName() {
    return getCanonicalText();
  }

  public String getReferenceName() {
    return myPatternExpression.getReferenceName();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public boolean isReferenceTo(PsiElement element) {
    return myPatternExpression.isReferenceTo(element);
  }

  public Object[] getVariants() {
    return myPatternExpression.getVariants();
  }

  public void processVariants(PsiScopeProcessor processor) {
    myPatternExpression.processVariants(processor);
  }

  public boolean isSoft() {
    return false;
  }

  // very special method
  public void setCachedResolveResult(PsiElement result, Boolean problemWithAccess, Boolean problemWithStatic) {
    ResolveCache resolveCache = ((PsiManagerImpl)getManager()).getResolveCache();
    resolveCache.setCachedJavaResolve(this, new ResolveResult[]{new CandidateInfo(result, PsiSubstitutor.EMPTY)}, true, true);
  }

  public String getMirrorText() {
    return getText();
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(myMirror == null);
    LOG.assertTrue(element.getElementType() == ElementType.REFERENCE_EXPRESSION);
    myMirror = element;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitReferenceExpression(this);
  }

  public String toString() {
    return "PsiReferenceExpression:" + getText();
  }

  public PsiType[] getTypeParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  public PsiElement getQualifier() {
    return getQualifierExpression();
  }
}
