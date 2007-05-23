package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class PsiDocParamRef extends CompositePsiElement implements PsiDocTagValue {
  private volatile PsiReference myCachedReference;

  public PsiDocParamRef() {
    super(DOC_PARAMETER_REF);
  }

  public void clearCaches() {
    myCachedReference = null;
    super.clearCaches();
  }

  public PsiReference getReference() {
    PsiReference cachedReference = myCachedReference;
    if (cachedReference != null) return cachedReference;
    final PsiDocCommentOwner owner = PsiTreeUtil.getParentOfType(this, PsiDocCommentOwner.class);
    if (!(owner instanceof PsiMethod) &&
        !(owner instanceof PsiClass)) return null;
    final ASTNode valueToken = findChildByType(JavaDocTokenType.DOC_TAG_VALUE_TOKEN);
    if (valueToken == null) return null;
    myCachedReference = cachedReference = new PsiReference() {
      public PsiElement resolve() {
        String name = valueToken.getText();
        final PsiElement firstChild = getFirstChild();
        if (firstChild instanceof PsiDocToken && ((PsiDocToken)firstChild).getTokenType().equals(JavaDocTokenType.DOC_TAG_VALUE_LT)) {
          final PsiTypeParameter[] typeParameters = ((PsiTypeParameterListOwner)owner).getTypeParameters();
          for (PsiTypeParameter typeParameter : typeParameters) {
            if (typeParameter.getName().equals(name)) return typeParameter;
          }
        }
        else if (owner instanceof PsiMethod) {
          final PsiParameter[] parameters = ((PsiMethod)owner).getParameterList().getParameters();
          for (PsiParameter parameter : parameters) {
            if (parameter.getName().equals(name)) return parameter;
          }
        }

        return null;
      }

      public String getCanonicalText() {
        return valueToken.getText();
      }

      public PsiElement handleElementRename(String newElementName) {
        final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(getNode());
        LeafElement newElement = Factory.createSingleLeafElement(JavaDocTokenType.DOC_TAG_VALUE_TOKEN, newElementName, 0, newElementName.length(), charTableByTree, getManager());
        replaceChild(valueToken, newElement);
        return PsiDocParamRef.this;
      }

      public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        if (isReferenceTo(element)) return PsiDocParamRef.this;
        if(!(element instanceof PsiParameter)) {
          throw new IncorrectOperationException("Unsupported operation");
        }
        return handleElementRename(((PsiParameter) element).getName());
      }

      public boolean isReferenceTo(PsiElement element) {
        if (!(element instanceof PsiNamedElement)) return false;
        PsiNamedElement namedElement = (PsiNamedElement)element;
        if (!getCanonicalText().equals(namedElement.getName())) return false;
        return element.getManager().areElementsEquivalent(element, resolve());
      }

      public Object[] getVariants() {
        final PsiElement firstChild = getFirstChild();
        if (firstChild instanceof PsiDocToken && ((PsiDocToken)firstChild).getTokenType().equals(JavaDocTokenType.DOC_TAG_VALUE_LT)) {
          return ((PsiTypeParameterListOwner)owner).getTypeParameters();
        } else if (owner instanceof PsiMethod) {
          return ((PsiMethod)owner).getParameterList().getParameters();
        }
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
      }

      public boolean isSoft(){
        return false;
      }

      public TextRange getRangeInElement() {
        final int startOffsetInParent = valueToken.getPsi().getStartOffsetInParent();
        return new TextRange(startOffsetInParent, startOffsetInParent + valueToken.getTextLength());
      }

      public PsiElement getElement() {
        return PsiDocParamRef.this;
      }
    };
    return cachedReference;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitDocTagValue(this);
  }
}
