package com.intellij.psi.impl.source.javadoc;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.util.CharTable;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.CharTable;

/**
 * @author mike
 */
class ParamDocTagInfo implements JavadocTagInfo {
  public String getName() {
    return "param";
  }

  public boolean isValidInContext(PsiElement element) {
    return element instanceof PsiMethod;
  }

  public Object[] getPossibleValues(PsiElement context, PsiElement place, String prefix) {
    PsiMethod method = (PsiMethod)context;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    return parameters;
  }

  public String checkTagValue(PsiDocTagValue value) {
    if (value == null) return "Parameter name expected";
    return null;
  }

  public PsiReference getReference(final PsiDocTagValue value) {
    return new PsiReference() {
      public PsiElement resolve() {
        return getParameter(value);
      }

      public String getCanonicalText() {
        return value.getText();
      }

      public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(SourceTreeToPsiMap.psiElementToTree(value.getFirstChild()));
        LeafElement newLeaf = Factory.createSingleLeafElement(ElementType.DOC_TAG_VALUE_TOKEN, newElementName.toCharArray(), 0, newElementName.length(), charTableByTree, null);
        return value.getFirstChild().replace(SourceTreeToPsiMap.treeElementToPsi(newLeaf));
      }

      public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
        throw new IncorrectOperationException("Not Implemented");
      }

      public boolean isReferenceTo(PsiElement element) {
        if (!(element instanceof PsiParameter)) return false;
        PsiParameter parameter = (PsiParameter)element;
        if (!getCanonicalText().equals(parameter.getName())) return false;
        return element.getManager().areElementsEquivalent(element, resolve());
      }

      public Object[] getVariants() {
        return getParameters(value);
      }

      public boolean isSoft(){
        return false;
      }

      public TextRange getRangeInElement() {
        return new TextRange(0, value.getTextLength());
      }

      public PsiElement getElement() {
        return value;
      }
    };
  }

  private PsiParameter getParameter(PsiDocTagValue value) {
    final PsiParameter[] parameters = getParameters(value);

    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (parameter.getName().equals(value.getText())) return parameter;
    }

    return null;
  }

  private PsiParameter[] getParameters(PsiDocTagValue value) {
    PsiMethod method = PsiTreeUtil.getParentOfType(value, PsiMethod.class);
    if (method == null) return PsiParameter.EMPTY_ARRAY;
    return method.getParameterList().getParameters();
  }


  public boolean isInline() {
    return false;
  }
}
