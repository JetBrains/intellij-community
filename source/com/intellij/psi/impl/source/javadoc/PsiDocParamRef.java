package com.intellij.psi.impl.source.javadoc;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.CharTable;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.CharTable;

/**
 * @author mike
 */
public class PsiDocParamRef extends CompositePsiElement implements PsiDocTagValue {
  public PsiDocParamRef() {
    super(DOC_PARAMETER_REF);
  }

  public PsiReference getReference() {
    PsiDocComment comment = PsiTreeUtil.getParentOfType(this, PsiDocComment.class);
    final PsiElement parent = comment.getParent();
    if (!(parent instanceof PsiMethod)) return null;
    final PsiMethod method = (PsiMethod)parent;
    if (method.getDocComment() != comment) return null;

    return new PsiReference() {
      public PsiElement resolve() {
        String name = getText();
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
          PsiParameter parameter = parameters[i];
          if (parameter.getName().equals(name)) return parameter;
        }

        return null;
      }

      public String getCanonicalText() {
        return getText();
      }

      public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        final TreeElement treeElement = SourceTreeToPsiMap.psiElementToTree(PsiDocParamRef.this);
        final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(treeElement);
        LeafElement newElement = Factory.createSingleLeafElement(ElementType.DOC_TAG_VALUE_TOKEN, newElementName.toCharArray(), 0, newElementName.length(), charTableByTree, getManager());
        replaceChildInternal(firstChild, newElement);
        return PsiDocParamRef.this;
      }

      public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
        if (isReferenceTo(element)) return PsiDocParamRef.this;
        if(!(element instanceof PsiParameter)) {
          throw new IncorrectOperationException("Unsupported operation");
        }
        return handleElementRename(((PsiParameter) element).getName());
      }

      public boolean isReferenceTo(PsiElement element) {
        if (!(element instanceof PsiParameter)) return false;
        PsiParameter parameter = (PsiParameter)element;
        if (!getCanonicalText().equals(parameter.getName())) return false;
        return element.getManager().areElementsEquivalent(element, resolve());
      }

      public Object[] getVariants() {
        return method.getParameterList().getParameters();
      }

      public boolean isSoft(){
        return false;
      }

      public TextRange getRangeInElement() {
        return new TextRange(0, getTextLength());
      }

      public PsiElement getElement() {
        return PsiDocParamRef.this;
      }
    };
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitDocTagValue(this);
  }
}
