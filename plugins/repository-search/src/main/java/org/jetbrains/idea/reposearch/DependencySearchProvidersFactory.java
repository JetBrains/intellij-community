package org.jetbrains.idea.reposearch;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;

@ApiStatus.Experimental
public interface DependencySearchProvidersFactory {
  ExtensionPointName<DependencySearchProvidersFactory> EXTENSION_POINT_NAME =
    ExtensionPointName.create("org.jetbrains.idea.reposearch.provider");

  boolean isApplicable(Project project);

  Collection<DependencySearchProvider> getProviders(Project project);
}
