package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.PomField;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.cache.FieldView;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class PsiEnumConstantImpl extends NonSlaveRepositoryPsiElement implements PsiEnumConstant {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiEnumConstantImpl");
  private String myCachedName = null;
  private Boolean myCachedIsDeprecated;
  private MyReference myReference = new MyReference();
  private Ref<PsiEnumConstantInitializer> myCachedInitializingClass = null;
  private PsiModifierListImpl myRepositoryModifierList = null;

  public PsiEnumConstantImpl(PsiManagerImpl manager, long repositoryId) {
    super(manager, repositoryId);
  }

  public String toString() {
    return "PsiEnumConstant:" + getName();
  }

  public PsiEnumConstantImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitEnumConstant(this);
  }

  public PsiExpressionList getArgumentList() {
    return (PsiExpressionList)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
  }

  public PsiEnumConstantInitializer getInitializingClass() {
    if (myCachedInitializingClass == null) {
      if (getTreeElement() != null) {
        myCachedInitializingClass = Ref.create((PsiEnumConstantInitializer)getTreeElement().findChildByRoleAsPsiElement(ChildRole.ANONYMOUS_CLASS));
      }
      else {
        long initializingClass = getRepositoryManager().getFieldView().getEnumConstantInitializer(getRepositoryId());
        if (initializingClass < 0) {
          myCachedInitializingClass = Ref.create((PsiEnumConstantInitializer)null);
        }
        else {
          PsiEnumConstantInitializer repoElement = (PsiEnumConstantInitializer)getRepositoryElementsManager().findOrCreatePsiElementById(initializingClass);
          myCachedInitializingClass = Ref.create(repoElement);
        }
      }
    }

    return myCachedInitializingClass.get();
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : null;
  }

  public PsiModifierList getModifierList() {
    if (getRepositoryId() >= 0) {
      if (myRepositoryModifierList == null) {
        myRepositoryModifierList = new PsiModifierListImpl(myManager, this);
      }
      return myRepositoryModifierList;
    }
    else {
      return (PsiModifierList)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.MODIFIER_LIST);
    }
  }

  public boolean hasModifierProperty(String name) {
    return (PsiModifier.PUBLIC.equals(name) || PsiModifier.STATIC.equals(name) || PsiModifier.FINAL.equals(name));
  }


  protected Object clone() {
    PsiEnumConstantImpl clone = (PsiEnumConstantImpl)super.clone();
    clone.myRepositoryModifierList = null;
    return clone;
  }

  public void setRepositoryId(long repositoryId) {
    super.setRepositoryId(repositoryId);

    if (repositoryId < 0) {
      if (myRepositoryModifierList != null) {
        myRepositoryModifierList.setOwner(this);
        myRepositoryModifierList = null;
      }
    }
    else {
      myRepositoryModifierList = (PsiModifierListImpl)bindSlave(ChildRole.MODIFIER_LIST);
    }
  }

  public PsiType getType() {
    return getManager().getElementFactory().createType(getContainingClass());
  }

  public PsiTypeElement getTypeElement() {
    return null;
  }

  public PsiExpression getInitializer() {
    return null;
  }

  public boolean hasInitializer() {
    return true;
  }

  public void normalizeDeclaration() throws IncorrectOperationException {}

  public Object computeConstantValue() {
    return null;
  }

  public PsiMethod resolveMethod() {
    PsiClass containingClass = getContainingClass();
    LOG.assertTrue(containingClass != null);
    ResolveResult resolveResult = getManager().getResolveHelper().resolveConstructor(getManager().getElementFactory().createType(containingClass), getArgumentList(), this);
    return (PsiMethod)resolveResult.getElement();
  }

  public ResolveResult resolveMethodGenerics() {
    PsiClass containingClass = getContainingClass();
    LOG.assertTrue(containingClass != null);
    return getManager().getResolveHelper().resolveConstructor(getManager().getElementFactory().createType(containingClass), getArgumentList(), this);
  }

  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  public String getName() {
    if (myCachedName == null) {
      if (getTreeElement() != null) {
        myCachedName = getNameIdentifier().getText();
      }
      else {
        myCachedName = getRepositoryManager().getFieldView().getName(getRepositoryId());
      }
    }
    return myCachedName;
  }

  public void subtreeChanged() {
    myCachedName = null;
    myCachedIsDeprecated = null;
    myCachedInitializingClass = null;
    super.subtreeChanged();
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    SharedPsiElementImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  public PsiDocComment getDocComment() {
    return (PsiDocComment)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
  }

  public boolean isDeprecated() {
    if (myCachedIsDeprecated == null) {
      boolean deprecated;
      if (getTreeElement() != null) {
        PsiDocComment docComment = getDocComment();
        deprecated = docComment != null && getDocComment().findTagByName("deprecated") != null;
        if (!deprecated) {
          deprecated = getModifierList().findAnnotation("java.lang.Deprecated") != null;
        }
      }
      else {
        FieldView fieldView = getRepositoryManager().getFieldView();
        deprecated = fieldView.isDeprecated(getRepositoryId());
        if (!deprecated && fieldView.mayBeDeprecatedByAnnotation(getRepositoryId())) {
          deprecated = getModifierList().findAnnotation("java.lang.Deprecated") != null;
        }
      }
      myCachedIsDeprecated = deprecated ? Boolean.TRUE : Boolean.FALSE;
    }
    return myCachedIsDeprecated.booleanValue();
  }

  public PsiReference getReference() {
    return myReference;
  }

  public PsiMethod resolveConstructor() {
    return resolveMethod();
  }

  public PomField getPom() {
    //TODO:
    return null;
  }

  private class MyReference implements PsiJavaReference {
    public PsiElement getElement() {
      return PsiEnumConstantImpl.this;
    }

    public TextRange getRangeInElement() {
      PsiIdentifier nameIdentifier = getNameIdentifier();
      LOG.assertTrue(nameIdentifier != null, getText());
      int startOffsetInParent = nameIdentifier.getStartOffsetInParent();
      return new TextRange(startOffsetInParent, startOffsetInParent + nameIdentifier.getTextLength());
    }

    public boolean isSoft() {
      return false;
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return getElement();
    }

    public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
      throw new IncorrectOperationException("Invalid operation");
    }

    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    public void processVariants(PsiScopeProcessor processor) {
    }

    public ResolveResult[] multiResolve(boolean incompleteCode) {
      PsiManager manager = getManager();
      PsiClassType type = manager.getElementFactory().createType(getContainingClass());
      return manager.getResolveHelper().multiResolveConstructor(type, getArgumentList(), getElement());
    }

    public ResolveResult advancedResolve(boolean incompleteCode) {
      final ResolveResult[] results = multiResolve(incompleteCode);
      if (results.length == 1) return results[0];
      return ResolveResult.EMPTY;
    }

    public PsiElement resolve() {
      return advancedResolve(false).getElement();
    }

    public String getCanonicalText() {
      String name = getContainingClass().getName();
      return name;
    }

    public boolean isReferenceTo(PsiElement element) {
      return element instanceof PsiMethod
             && ((PsiMethod)element).isConstructor()
             && ((PsiMethod)element).getContainingClass() == getContainingClass()
             && getManager().areElementsEquivalent(resolve(), element);
    }
  }


}
