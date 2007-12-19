package com.intellij.psi.impl.source;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.impl.source.tree.*;
import org.jetbrains.annotations.NotNull;

public class PsiImportStatementImpl extends PsiImportStatementBaseImpl implements PsiImportStatement {
  public static final PsiImportStatementImpl[] EMPTY_ARRAY = new PsiImportStatementImpl[0];

  public PsiImportStatementImpl(PsiManagerEx manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public PsiImportStatementImpl(PsiManagerEx manager, SrcRepositoryPsiElement owner, int index) {
    super(manager, owner, index);
  }

  public String getQualifiedName() {
    final PsiJavaCodeReferenceElement mirrorReference = getMirrorReference();
    return mirrorReference == null ? null : mirrorReference.getCanonicalText();
  }

  public PsiJavaCodeReferenceElement getImportReference() {
    return (PsiJavaCodeReferenceElement)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.IMPORT_REFERENCE);
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitImportStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiImportStatement";
  }

  public PsiJavaCodeReferenceElement getMirrorReference() {
    if (myOwner != null){
      PsiJavaCodeReferenceElementImpl refElement = (PsiJavaCodeReferenceElementImpl)getCachedMirrorReference();
      if (refElement == null){
        CompositeElement treeElement = getTreeElement();
        if (treeElement != null){
          refElement = (PsiJavaCodeReferenceElementImpl)treeElement.findChildByRole(ChildRole.IMPORT_REFERENCE);
        }
        else{
          final FileElement holderElement = new JavaDummyHolder(myManager, this).getTreeElement();
          final String refText = getRepositoryManager().getFileView().getImportQualifiedName(getRepositoryId(), getIndex());
          if (refText == null) return null;
          refElement = (PsiJavaCodeReferenceElementImpl) Parsing.parseJavaCodeReferenceText(myManager, refText, holderElement.getCharTable());
          if(refElement == null) return null;
          TreeUtil.addChildren(holderElement, refElement);
          refElement.setKindWhenDummy(
              isOnDemand()
              ? PsiJavaCodeReferenceElementImpl.CLASS_FQ_OR_PACKAGE_NAME_KIND
              : PsiJavaCodeReferenceElementImpl.CLASS_FQ_NAME_KIND);
        }
        setCachedMirrorReference(refElement);
      }
      return refElement;
    }
    else{
      return (PsiJavaCodeReferenceElement)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.IMPORT_REFERENCE);
    }
  }
}

