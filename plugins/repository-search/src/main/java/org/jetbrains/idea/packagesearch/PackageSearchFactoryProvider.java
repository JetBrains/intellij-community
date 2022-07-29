package org.jetbrains.idea.packagesearch;

import com.intellij.application.options.RegistryManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.reposearch.DependencySearchProvider;
import org.jetbrains.idea.reposearch.DependencySearchProvidersFactory;

import java.util.Collection;
import java.util.Collections;

final class PackageSearchFactoryProvider implements DependencySearchProvidersFactory {
  @Override
  public Collection<DependencySearchProvider> getProviders(@NotNull Project project) {
    if (!RegistryManager.getInstance().is("maven.packagesearch.enabled")) {
      return Collections.emptyList();
    }
    return Collections.singleton(new PackageSearchService());
  }
}
