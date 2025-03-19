// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

data class MavenProjectsTreeUpdateResult(val updated: Map<MavenProject, MavenProjectChanges>, val deleted: List<MavenProject>) {
  constructor(updated: List<com.intellij.openapi.util.Pair<MavenProject, MavenProjectChanges>>, deleted: List<MavenProject>)
    : this(updated.associate { Pair(it.first, it.second) }, deleted)

  operator fun plus(other: MavenProjectsTreeUpdateResult): MavenProjectsTreeUpdateResult {
    return MavenProjectsTreeUpdateResult(this.updated + other.updated, this.deleted + other.deleted)
  }
}
