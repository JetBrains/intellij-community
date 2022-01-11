package org.jetbrains.idea.reposearch;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;

@ApiStatus.Experimental
public interface DependencySearchProvidersFactory {
  boolean isApplicable(Project project);

  Collection<DependencySearchProvider> getProviders(Project project);
}
