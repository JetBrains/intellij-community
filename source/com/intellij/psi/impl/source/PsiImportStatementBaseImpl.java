package com.intellij.psi.impl.source;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.util.PatchedSoftReference;
import com.intellij.util.PatchedWeakReference;

import java.lang.ref.Reference;

/**
 * @author dsl
 */
public abstract class PsiImportStatementBaseImpl extends IndexedRepositoryPsiElement implements PsiImportStatementBase{
  private Reference myCachedMirrorReference = null;
  private Boolean myCachedIsOnDemand = null;

  protected PsiImportStatementBaseImpl(PsiManagerImpl manager, SrcRepositoryPsiElement owner, int index) {
    super(manager, owner, index);
  }

  protected PsiImportStatementBaseImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedIsOnDemand = null;
    setCachedMirrorReference(null);
  }

  public void setOwnerAndIndex(SrcRepositoryPsiElement owner, int index) {
    super.setOwnerAndIndex(owner, index);
    setCachedMirrorReference(null);
  }

  protected Object clone() {
    PsiImportStatementBaseImpl clone = (PsiImportStatementBaseImpl)super.clone();
    clone.setCachedMirrorReference(null);
    return clone;
  }

  public boolean isOnDemand(){
    if (myCachedIsOnDemand == null){
      boolean onDemand;
      if (getTreeElement() != null){
        onDemand = calcTreeElement().findChildByRoleAsPsiElement(ChildRole.IMPORT_ON_DEMAND_DOT) != null;
      }
      else{
        onDemand = getRepositoryManager().getFileView().isImportOnDemand(getRepositoryId(), getIndex());
      }
      myCachedIsOnDemand = onDemand ? Boolean.TRUE : Boolean.FALSE;
    }
    return myCachedIsOnDemand.booleanValue();
  }

  public PsiElement resolve() {
    final PsiJavaCodeReferenceElement mirrorReference = getMirrorReference();
    return mirrorReference == null ? null : mirrorReference.resolve();
  }

  protected abstract PsiJavaCodeReferenceElement getMirrorReference();

  protected PsiJavaCodeReferenceElement getCachedMirrorReference() {
    return myCachedMirrorReference == null ? null : (PsiJavaCodeReferenceElement)myCachedMirrorReference.get();
  }

  protected void setCachedMirrorReference(PsiJavaCodeReferenceElement refElement) {
    if (refElement == null) {
      myCachedMirrorReference = null;
    }
    else {
      myCachedMirrorReference = myManager.isBatchFilesProcessingMode()
                                ? new PatchedWeakReference(refElement)
                                : (Reference)new PatchedSoftReference(refElement);
    }
  }
}
