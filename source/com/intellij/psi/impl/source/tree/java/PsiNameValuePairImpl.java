package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */

//Retrieves method reference from this pair, do NOT reuse!!!
public class PsiNameValuePairImpl extends CompositePsiElement implements PsiNameValuePair {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiNameValuePairImpl");
  private volatile String myCachedName = null;
  private volatile PsiIdentifier myCachedNameIdentifier = null;
  private volatile PsiAnnotationMemberValue myCachedValue = null;
  private volatile boolean myNameCached = false;

  public void clearCaches() {
    myNameCached = false;
    myCachedName = null;
    myCachedNameIdentifier = null;
    myCachedValue = null;
    super.clearCaches();
  }

  public PsiNameValuePairImpl() {
    super(NAME_VALUE_PAIR);
  }

  public PsiIdentifier getNameIdentifier() {
    PsiIdentifier cachedNameIdentifier = myCachedNameIdentifier;
    if (!myNameCached) {
      myCachedNameIdentifier = cachedNameIdentifier = (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.NAME);
      myCachedName = cachedNameIdentifier == null ? null : cachedNameIdentifier.getText();
      myNameCached = true;
    }
    return cachedNameIdentifier;
  }

  public String getName() {
    String cachedName = myCachedName;
    if (!myNameCached) {
      PsiIdentifier identifier;
      myCachedNameIdentifier = identifier = (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.NAME);
      myCachedName = cachedName = identifier == null ? null : identifier.getText();
      myNameCached = true;
    }
    return cachedName;
  }

  public PsiAnnotationMemberValue getValue() {
    PsiAnnotationMemberValue cachedValue = myCachedValue;
    if (cachedValue == null) {
      myCachedValue = cachedValue = (PsiAnnotationMemberValue)findChildByRoleAsPsiElement(ChildRole.ANNOTATION_VALUE);
    }

    return cachedValue;
  }

  public int getChildRole(ASTNode child) {
    if (ANNOTATION_MEMBER_VALUE_BIT_SET.contains(child.getElementType())) {
      return ChildRole.ANNOTATION_VALUE;
    }
    else if (child.getElementType() == IDENTIFIER) {
      return ChildRole.NAME;
    }
    else if (child.getElementType() == EQ) {
      return ChildRole.OPERATION_SIGN;
    }

    return ChildRole.NONE;
  }

  public ASTNode findChildByRole(int role) {
    if (role == ChildRole.NAME) {
      return TreeUtil.findChild(this, IDENTIFIER);
    }
    else if (role == ChildRole.ANNOTATION_VALUE) {
      return TreeUtil.findChild(this, ANNOTATION_MEMBER_VALUE_BIT_SET);
    }
    else if (role == ChildRole.OPERATION_SIGN) {
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
          final TreeElement firstChildNode = getFirstChildNode();
          if (firstChildNode != null && firstChildNode.getElementType() == REFERENCE_EXPRESSION) {
            final PsiReferenceExpression refExpr = (PsiReferenceExpression)firstChildNode.getPsi();
            if (refExpr.isQualified()) return new TextRange(0, 0);
            return new TextRange(0, refExpr.getTextLength());
          }
          else {
            return new TextRange(0, 0);
          }
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

      public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        throw new IncorrectOperationException("Not implemented");
      }

      public boolean isReferenceTo(PsiElement element) {
        return element instanceof PsiMethod && element.equals(resolve());
      }

      public Object[] getVariants() {
        PsiClass aClass = getReferencedClass();
        if (aClass != null) {
          PsiAnnotationParameterList parent = (PsiAnnotationParameterList)getParent();
          final PsiNameValuePair[] existingPairs = parent.getAttributes();
          List<PsiMethod> result = new ArrayList<PsiMethod>();
methods:
          for (PsiMethod method : aClass.getMethods()) {
            for (PsiNameValuePair pair : existingPairs) {
              if (Comparing.equal(pair.getName(), method.getName())) continue methods;
            }
            result.add(method);
          }

          return result.toArray(new Object[result.size()]);
        } else {
          return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }
      }

      public boolean isSoft() {
        return false;
      }
    };
  }

  public final void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitNameValuePair(this);
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    ChameleonTransforming.transformChildren(this);
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
    final TreeElement treeElement = super.addInternal(first, last, anchor, before);
    if (first == last && first.getElementType() == ElementType.IDENTIFIER) {
      LeafElement eq = Factory.createSingleLeafElement(ElementType.EQ, "=", 0, 1, treeCharTab, getManager());
      super.addInternal(eq, eq, first, Boolean.FALSE);
    }
    return treeElement;
  }

  public void deleteChildInternal(@NotNull ASTNode child) {
    super.deleteChildInternal(child);
    if (child.getElementType() == JavaTokenType.IDENTIFIER) {
      super.deleteChildInternal(findChildByRole(ChildRole.OPERATION_SIGN));
    }
  }
}
