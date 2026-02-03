package org.jetbrains.idea.reposearch;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Experimental
public interface DependencySearchProvidersFactory {
  Collection<DependencySearchProvider> getProviders(@NotNull Project project);
}
