// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface DependencyCompletionProviderFactory {

  ExtensionPointName<DependencyCompletionProviderFactory> EP_NAME =
    ExtensionPointName.create("org.jetbrains.idea.maven.dependencyCompletionProviderFactory");

  boolean isApplicable(Project project);

  @NotNull
  List<DependencyCompletionProvider> getProviders(Project project);
}
