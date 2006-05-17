package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.util.CharTable;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;
import com.intellij.lang.ASTNode;

/**
 * @author ven
 */

//Retrieves method reference from this pair, do NOT reuse!!!
public class PsiNameValuePairImpl extends CompositePsiElement implements PsiNameValuePair {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiNameValuePairImpl");
  private String myCachedName = null;
  private PsiIdentifier myCachedNameIdentifier = null;
  private PsiAnnotationMemberValue myCachedValue = null;
  private boolean myNameCached = false;

  public void clearCaches() {
    myCachedName = null;
    myCachedNameIdentifier = null;
    myCachedValue = null;
    myNameCached = false;
    super.clearCaches();
  }

  public PsiNameValuePairImpl() {
    super(NAME_VALUE_PAIR);
  }

  public PsiIdentifier getNameIdentifier() {
    if (!myNameCached) {
      myCachedNameIdentifier = (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.NAME);
      myCachedName = myCachedNameIdentifier == null ? null : myCachedNameIdentifier.getText();
      myNameCached = true;
    }
    return myCachedNameIdentifier;
  }

  public String getName() {
    if (!myNameCached) {
      myCachedNameIdentifier = (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.NAME);
      myCachedName = myCachedNameIdentifier == null ? null : myCachedNameIdentifier.getText();
      myNameCached = true;
    }
    return myCachedName;
  }

  public PsiAnnotationMemberValue getValue() {
    if (myCachedValue == null) {
      myCachedValue = (PsiAnnotationMemberValue)findChildByRoleAsPsiElement(ChildRole.ANNOTATION_VALUE);
    }

    return myCachedValue;
  }

  public int getChildRole(ASTNode child) {
    if (ANNOTATION_MEMBER_VALUE_BIT_SET.contains(child.getElementType())) {
      return ChildRole.ANNOTATION_VALUE;
    } else if (child.getElementType() == IDENTIFIER) {
      return ChildRole.NAME;
    } else if (child.getElementType() == EQ) {
      return ChildRole.OPERATION_SIGN;
    }

    return ChildRole.NONE;
  }

  public ASTNode findChildByRole(int role) {
    if (role == ChildRole.NAME) {
      return TreeUtil.findChild(this, IDENTIFIER);
    } else if (role == ChildRole.ANNOTATION_VALUE) {
      return TreeUtil.findChild(this, ANNOTATION_MEMBER_VALUE_BIT_SET);
    } else if (role == ChildRole.OPERATION_SIGN) {
      return TreeUtil.findChild(this, EQ);
    }

    return null;
  }

  public String toString() {
    return "PsiNameValuePair";
  }

  public PsiReference getReference() {
    return new PsiReference() {
      private PsiClass getReferencedClass () {
        LOG.assertTrue(getTreeParent().getElementType() == ANNOTATION_PARAMETER_LIST && getTreeParent().getTreeParent().getElementType() == ANNOTATION);
        PsiAnnotationImpl annotation = (PsiAnnotationImpl)getTreeParent().getTreeParent().getPsi();
        PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
        return nameRef == null ? null : (PsiClass)nameRef.resolve();
      }

      public PsiElement getElement() {
        PsiIdentifier nameIdentifier = getNameIdentifier();
        if (nameIdentifier != null) {
          return nameIdentifier;
        } else {
          return PsiNameValuePairImpl.this;
        }
      }

      public TextRange getRangeInElement() {
        PsiIdentifier id = getNameIdentifier();
        if (id != null) {
          return new TextRange(0, id.getTextLength());
        }
        else {
          return new TextRange(0, 0);
        }
      }

      public PsiElement resolve() {
        PsiClass refClass = getReferencedClass();
        if (refClass == null) return null;
        String name = getName();
        if (name == null) name = PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME;
        MethodSignature signature = MethodSignatureUtil.createMethodSignature(name, PsiType.EMPTY_ARRAY, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
        return MethodSignatureUtil.findMethodBySignature(refClass, signature, false);
      }

      public String getCanonicalText() {
        String name = getName();
        return name != null ? name : PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME;
      }

      public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        PsiIdentifier nameIdentifier = getNameIdentifier();
        if (nameIdentifier != null) {
          SharedPsiElementImplUtil.setName(nameIdentifier, newElementName);
        }
        else if (ANNOTATION_MEMBER_VALUE_BIT_SET.contains(getFirstChildNode().getElementType())) {
          PsiElementFactory factory = getManager().getElementFactory();
          nameIdentifier = factory.createIdentifier(newElementName);
          addBefore(nameIdentifier, SourceTreeToPsiMap.treeElementToPsi(getFirstChildNode()));
        }

        return PsiNameValuePairImpl.this;
      }

      public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
        throw new IncorrectOperationException("Not implemented");
      }

      public boolean isReferenceTo(PsiElement element) {
        if (element instanceof PsiMethod) {
          return element.equals(resolve());
        }

        return false;
      }

      public Object[] getVariants() {
        PsiClass aClass = getReferencedClass();
        if (aClass != null) {
          return aClass.getMethods();
        } else {
          return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }
      }

      public boolean isSoft() {
        return false;
      }
    };
  }

  public final void accept(PsiElementVisitor visitor) {
    visitor.visitNameValuePair(this);
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    ChameleonTransforming.transformChildren(this);
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
    final TreeElement treeElement = super.addInternal(first, last, anchor, before);
    if (first == last && first.getElementType() == ElementType.IDENTIFIER) {
      LeafElement eq = Factory.createSingleLeafElement(ElementType.EQ, new char[]{'='}, 0, 1, treeCharTab, getManager());
      super.addInternal(eq, eq, first, Boolean.FALSE);
    }
    return treeElement;
  }

  public void deleteChildInternal(ASTNode child) {
    super.deleteChildInternal(child);
    if (child.getElementType() == ElementType.IDENTIFIER) {
      super.deleteChildInternal(findChildByRole(ChildRole.OPERATION_SIGN));
    }
  }
}
