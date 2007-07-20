package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.RepositoryElementsManager;
import com.intellij.psi.impl.RepositoryPsiElement;
import com.intellij.psi.impl.cache.RepositoryManager;
import org.jetbrains.annotations.NotNull;

public abstract class ClsRepositoryPsiElement extends ClsElementImpl implements RepositoryPsiElement{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsRepositoryPsiElement");

  protected final PsiManagerImpl myManager;
  private volatile long myRepositoryId;
  private volatile long myCachedParentId = -1;

  public ClsRepositoryPsiElement(@NotNull PsiManagerImpl manager, long repositoryId) {
    myManager = manager;
    myRepositoryId = repositoryId;
  }

  public long getRepositoryId() {
    return myRepositoryId;
  }

  public boolean isRepositoryIdInitialized() {
    return true;
  }

  public void setRepositoryId(long repositoryId) {
    myRepositoryId = repositoryId;
    myCachedParentId = -1;
  }

  protected long getParentId(){
    if(myCachedParentId > 0) return myCachedParentId;
    long repositoryId = getRepositoryId();
    if (repositoryId < 0) return -1;
    myCachedParentId = getRepositoryManager().getItemView(repositoryId).getParent(repositoryId);
    return myCachedParentId;
  }


  public void prepareToRepositoryIdInvalidation() {
    LOG.assertTrue(false);
  }

  public final PsiManager getManager() {
    return myManager;
  }

  protected RepositoryManager getRepositoryManager() {
    return myManager.getRepositoryManager();
  }

  protected RepositoryElementsManager getRepositoryElementsManager() {
    return myManager.getRepositoryElementsManager();
  }

}
