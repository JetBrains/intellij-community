package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.RepositoryLocation;

/**
 * @author yole
 */
class CompositeRepositoryLocation implements RepositoryLocation {
  private CommittedChangesProvider myProvider;
  private RepositoryLocation myProviderLocation;

  public CompositeRepositoryLocation(final CommittedChangesProvider provider, final RepositoryLocation providerLocation) {
    myProvider = provider;
    myProviderLocation = providerLocation;
  }

  public String toString() {
    return myProviderLocation.toString();
  }

  public String toPresentableString() {
    return myProviderLocation.toPresentableString();
  }

  public CommittedChangesProvider getProvider() {
    return myProvider;
  }

  public RepositoryLocation getProviderLocation() {
    return myProviderLocation;
  }
}
