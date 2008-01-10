/*
 * @author max
 */
package com.intellij.psi.impl;

import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.cache.impl.RepositoryManagerImpl;

public class PsiManagerConfigurationImpl extends PsiManagerConfiguration {
  public PsiManagerConfigurationImpl() {
    REPOSITORY_ENABLED = true;
  }

  @Override
  public RepositoryElementsManager createRepositoryElementsManager(final PsiManagerImpl manager, final RepositoryManager repositoryManager) {
    if (!REPOSITORY_ENABLED) return super.createRepositoryElementsManager(manager, repositoryManager);
    return new RepositoryElementsManagerImpl(manager, repositoryManager);
  }

  @Override
  public RepositoryManager createRepositoryManager(final PsiManagerImpl manager) {
    if (!REPOSITORY_ENABLED) return super.createRepositoryManager(manager);
    return new RepositoryManagerImpl(manager);
  }
}