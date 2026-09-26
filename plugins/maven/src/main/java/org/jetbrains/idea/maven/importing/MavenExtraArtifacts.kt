// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MavenExtraArtifacts")

package org.jetbrains.idea.maven.importing

import com.intellij.openapi.util.Pair
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.project.MavenProject

fun MavenProject.getClassifierAndExtension(artifact: MavenArtifact, type: MavenExtraArtifactType): Pair<String, String> {
  for (each in MavenImporter.getSuitableImporters(this)) {
    val result = each.getExtraArtifactClassifierAndExtension(artifact, type)
    if (result != null) return result
  }
  return Pair.create(type.defaultClassifier, type.defaultExtension)
}
