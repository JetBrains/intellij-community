package org.jetbrains.idea.reposearch;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

import java.util.Collection;

public interface DependencySearchProvidersFactory {
  ExtensionPointName<DependencySearchProvidersFactory> EXTENSION_POINT_NAME =
    ExtensionPointName.create("org.jetbrains.idea.repository.search.providerFactory");

  Collection<DependencySearchProvider> getProviders(Project project);
}
