package com.intellij.psi.impl.source;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.impl.source.tree.*;

public class PsiImportStatementImpl extends PsiImportStatementBaseImpl implements PsiImportStatement {

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

  public void accept(PsiElementVisitor visitor){
    visitor.visitImportStatement(this);
  }

  public String toString(){
    return "PsiImportStatement";
  }

  public PsiJavaCodeReferenceElement getMirrorReference() {
    if (myOwner != null){
      if (getCachedMirrorReference() == null){
        CompositeElement treeElement = getTreeElement();
        if (treeElement != null){
          setCachedMirrorReference((PsiJavaCodeReferenceElementImpl) treeElement.findChildByRole(ChildRole.IMPORT_REFERENCE));
        }
        else{
          final FileElement holderElement = new DummyHolder(myManager, this).getTreeElement();
          final String refText = getRepositoryManager().getFileView().getImportQualifiedName(getRepositoryId(), getIndex());
          if (refText == null) return null;
          PsiJavaCodeReferenceElementImpl mirrorRef = (PsiJavaCodeReferenceElementImpl) Parsing.parseJavaCodeReferenceText(myManager, refText, holderElement.getCharTable());
          if(mirrorRef == null) return null;
          setCachedMirrorReference(mirrorRef);
          TreeUtil.addChildren(holderElement, mirrorRef);
          mirrorRef.setKindWhenDummy(
              isOnDemand()
              ? PsiJavaCodeReferenceElementImpl.CLASS_FQ_OR_PACKAGE_NAME_KIND
              : PsiJavaCodeReferenceElementImpl.CLASS_FQ_NAME_KIND);
        }
      }
      return getCachedMirrorReference();
    }
    else{
      return (PsiJavaCodeReferenceElement)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.IMPORT_REFERENCE);
    }
  }
}

