package com.intellij.psi.impl.source;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.PomField;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.cache.FieldView;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class PsiEnumConstantImpl extends NonSlaveRepositoryPsiElement implements PsiEnumConstant {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiEnumConstantImpl");
  private MyReference myReference = new MyReference();
  private PsiModifierListImpl myRepositoryModifierList = null;
  private String myCachedName = null;
  private Boolean myCachedIsDeprecated;
  private Ref<PsiEnumConstantInitializer> myCachedInitializingClass = null;

  public PsiEnumConstantImpl(PsiManagerEx manager, long repositoryId) {
    super(manager, repositoryId);
  }

  public String toString() {
    return "PsiEnumConstant:" + getName();
  }

  public PsiEnumConstantImpl(PsiManagerEx manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitEnumConstant(this);
  }

  public PsiExpressionList getArgumentList() {
    return (PsiExpressionList)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
  }

  public PsiEnumConstantInitializer getInitializingClass() {
    if (myCachedInitializingClass == null) {
      if (getTreeElement() != null) {
        myCachedInitializingClass = Ref.create((PsiEnumConstantInitializer)getTreeElement()
                                                 .findChildByRoleAsPsiElement(ChildRole.ANONYMOUS_CLASS));
      }
      else {
        long initializingClass = getRepositoryManager().getFieldView().getEnumConstantInitializer(getRepositoryId());
        if (initializingClass < 0) {
          myCachedInitializingClass = Ref.create(null);
        }
        else {
          PsiEnumConstantInitializer repoElement = (PsiEnumConstantInitializer)getRepositoryElementsManager()
            .findOrCreatePsiElementById(initializingClass);
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
      synchronized (PsiLock.LOCK) {
        if (myRepositoryModifierList == null) {
          myRepositoryModifierList = new PsiModifierListImpl(myManager, this);
        }
        return myRepositoryModifierList;
      }
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
    clone.dropCached();
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

    dropCached();
  }

  @NotNull
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

  public void normalizeDeclaration() throws IncorrectOperationException { }

  public Object computeConstantValue() {
    return this;
  }

  public PsiMethod resolveMethod() {
    PsiClass containingClass = getContainingClass();
    LOG.assertTrue(containingClass != null);
    JavaResolveResult resolveResult = getManager().getResolveHelper()
      .resolveConstructor(getManager().getElementFactory().createType(containingClass), getArgumentList(), this);
    return (PsiMethod)resolveResult.getElement();
  }

  @NotNull
  public JavaResolveResult resolveMethodGenerics() {
    PsiClass containingClass = getContainingClass();
    LOG.assertTrue(containingClass != null);
    return getManager().getResolveHelper()
      .resolveConstructor(getManager().getElementFactory().createType(containingClass), getArgumentList(), this);
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
    super.subtreeChanged();
    dropCached();
  }

  private void dropCached() {
    myCachedName = null;
    myCachedIsDeprecated = null;
    myCachedInitializingClass = null;
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
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
        deprecated = docComment != null && docComment.findTagByName("deprecated") != null;
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

    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      throw new IncorrectOperationException("Invalid operation");
    }

    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    public void processVariants(PsiScopeProcessor processor) {
    }

    @NotNull
    public JavaResolveResult[] multiResolve(boolean incompleteCode) {
      PsiManager manager = getManager();
      PsiClassType type = manager.getElementFactory().createType(getContainingClass());
      return manager.getResolveHelper().multiResolveConstructor(type, getArgumentList(), getElement());
    }

    @NotNull
    public JavaResolveResult advancedResolve(boolean incompleteCode) {
      final JavaResolveResult[] results = multiResolve(incompleteCode);
      if (results.length == 1) return results[0];
      return JavaResolveResult.EMPTY;
    }

    public PsiElement resolve() {
      return advancedResolve(false).getElement();
    }

    public String getCanonicalText() {
      return getContainingClass().getName();
    }

    public boolean isReferenceTo(PsiElement element) {
      return element instanceof PsiMethod
             && ((PsiMethod)element).isConstructor()
             && ((PsiMethod)element).getContainingClass() == getContainingClass()
             && getManager().areElementsEquivalent(resolve(), element);
    }
  }

  public ItemPresentation getPresentation() {
    return SymbolPresentationUtil.getFieldPresentation(this);
  }
  public void setInitializer(PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }
}
