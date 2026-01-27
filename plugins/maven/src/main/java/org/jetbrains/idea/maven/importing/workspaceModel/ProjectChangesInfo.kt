// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectModifications
import kotlin.collections.component1
import kotlin.collections.component2

@Internal
internal data class ProjectChangesInfo(val allProjectsToChanges: Map<MavenProject, MavenProjectModifications>) {
  val projectFilePaths: List<String> get() = allProjectsToChanges.keys.map { it.path }
  val hasChanges: Boolean = allProjectsToChanges.values.any { it == MavenProjectModifications.ALL }
  val changedProjectsOnly: Iterable<MavenProject>
    get() = allProjectsToChanges
      .asSequence()
      .filter { (_, changes) -> changes == MavenProjectModifications.ALL }
      .map { (mavenProject, _) -> mavenProject }
      .asIterable()
}