package org.jetbrains.idea.kpmsearch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.idea.reposearch.DependencySearchProvider;
import org.jetbrains.idea.reposearch.DependencySearchProvidersFactory;

import java.util.Collection;
import java.util.Collections;

public class KpmSearchFactoryProvider implements DependencySearchProvidersFactory {
  @Override
  public boolean isApplicable(Project project) {
    return Registry.is("maven.packagesearch.enabled");
  }

  @Override
  public Collection<DependencySearchProvider> getProviders(Project project) {
    return Collections.singleton(new PackageSearchService());
  }
}
