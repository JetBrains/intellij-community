// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;

public record MavenProjectWithHolder(@NotNull MavenProject mavenProject,
                                     @NotNull NativeMavenProjectHolder mavenProjectHolder,
                                     @NotNull MavenProjectChanges changes) {
}
