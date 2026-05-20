// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MavenDependencyTypes")

package org.jetbrains.idea.maven.importing

import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.SupportedRequestType

fun MavenProject.getDependencyTypesFromImporters(type: SupportedRequestType): Set<String> {
  val res = HashSet<String>()
  for (each in MavenImporter.getSuitableImporters(this)) {
    each.getSupportedDependencyTypes(res, type)
  }
  return res
}

val MavenProject.supportedDependencyScopes: Set<String>
  get() {
    val result = hashSetOf(
      MavenConstants.SCOPE_COMPILE,
      MavenConstants.SCOPE_PROVIDED,
      MavenConstants.SCOPE_RUNTIME,
      MavenConstants.SCOPE_TEST,
      MavenConstants.SCOPE_SYSTEM
    )
    for (each in MavenImporter.getSuitableImporters(this)) {
      each.getSupportedDependencyScopes(result)
    }
    return result
  }
