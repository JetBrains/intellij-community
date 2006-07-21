package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.cache.DeclarationView;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.IndexedRepositoryPsiElement;
import com.intellij.psi.impl.source.SrcRepositoryPsiElement;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class PsiAnnotationImpl extends IndexedRepositoryPsiElement implements PsiModifier, PsiAnnotation {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl");
  private CompositeElement myParsedFromRepository;

  public PsiAnnotationImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public PsiAnnotationImpl(PsiManagerImpl manager, SrcRepositoryPsiElement owner, int index) {
    super(manager, owner, index);
  }

  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    return (PsiJavaCodeReferenceElement)getMirrorTreeElement().findChildByRoleAsPsiElement(ChildRole.CLASS_REFERENCE);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myParsedFromRepository = null;
  }

  protected Object clone() {
    final PsiAnnotationImpl clone = (PsiAnnotationImpl)super.clone();
    clone.myParsedFromRepository = null;
    return clone;
  }

  private CompositeElement getMirrorTreeElement() {
    CompositeElement actualTree = getTreeElement();
    if (actualTree != null) {
      myParsedFromRepository = null;
      return actualTree;
    }

    if (myParsedFromRepository != null) return myParsedFromRepository;
    PsiElement owner = myOwner.getParent();
    long parentId = ((SrcRepositoryPsiElement)owner).getRepositoryId();
    DeclarationView view = (DeclarationView)getRepositoryManager().getItemView(parentId);
    final String text = view.getAnnotations(parentId)[getIndex()];
    try {
      myParsedFromRepository = (CompositeElement)myManager.getElementFactory().createAnnotationFromText(text, this).getNode();
      return myParsedFromRepository;
    }
    catch (IncorrectOperationException e) {
      LOG.error("Bad annotation in repository!");
      return null;
    }
  }

  public PsiAnnotationMemberValue findAttributeValue(String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  public String toString() {
    return "PsiAnnotation";
  }

  @NotNull
  public PsiAnnotationParameterList getParameterList() {
    return (PsiAnnotationParameterList)getMirrorTreeElement().findChildByRoleAsPsiElement(ChildRole.PARAMETER_LIST);
  }

  @Nullable public String getQualifiedName() {
    final PsiJavaCodeReferenceElement nameRef = getNameReferenceElement();
    if (nameRef == null) return null;
    return nameRef.getCanonicalText();
  }

  public final void accept(PsiElementVisitor visitor) {
    visitor.visitAnnotation(this);
  }

  public PsiMetaDataBase getMetaData() {
    return MetaRegistry.getMetaBase(this);
  }

}
