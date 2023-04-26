/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * @author Vladislav.Soroka
 */
public class MavenEmbedderSettings implements Serializable {
  @NotNull
  private final MavenServerSettings settings;
  @Nullable
  private final String multiModuleProjectDirectory;
  private final boolean forceResolveDependenciesSequentially;
  private final boolean useCustomDependenciesResolver;

  public MavenEmbedderSettings(@NotNull MavenServerSettings settings) {
    this(settings, null, false, true);
  }

  public MavenEmbedderSettings(@NotNull MavenServerSettings settings,
                               @Nullable String multiModuleProjectDirectory,
                               boolean forceResolveDependenciesSequentially,
                               boolean useCustomDependenciesResolver) {
    this.settings = settings;
    this.multiModuleProjectDirectory = multiModuleProjectDirectory;
    this.forceResolveDependenciesSequentially = forceResolveDependenciesSequentially;
    this.useCustomDependenciesResolver = useCustomDependenciesResolver;
  }

  @NotNull
  public MavenServerSettings getSettings() {
    return settings;
  }

  @Nullable
  public String getMultiModuleProjectDirectory() {
    return multiModuleProjectDirectory;
  }

  public boolean forceResolveDependenciesSequentially() {
    return forceResolveDependenciesSequentially;
  }

  public boolean useCustomDependenciesResolver() {
    return useCustomDependenciesResolver;
  }
}
