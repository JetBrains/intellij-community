// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.completion

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import java.util.function.Consumer

interface MavenDependencySearchContributor {
  fun suggestPrefixBlocking(
    groupId: String,
    artifactId: String,
    useCache: Boolean,
    useLocalOnly: Boolean,
    consumer: Consumer<MavenRepoArtifactInfo>,
  ): Promise<Int>

  fun fulltextSearchBlocking(
    searchString: String,
    useCache: Boolean,
    useLocalOnly: Boolean,
    consumer: Consumer<MavenRepoArtifactInfo>,
  ): Promise<Int>

  suspend fun fulltextSearch(
    searchString: String,
    useCache: Boolean,
    useLocalOnly: Boolean,
    consumer: Consumer<MavenRepoArtifactInfo>,
  )

  suspend fun suggestPrefix(
    groupId: String,
    artifactId: String,
    useCache: Boolean,
    useLocalOnly: Boolean,
    consumer: Consumer<MavenRepoArtifactInfo>,
  )

  fun getGroupIdsBlocking(pattern: String?): Set<String>

  suspend fun getGroupIds(pattern: String?): Set<String>

  fun getArtifactIdsBlocking(groupId: String): Set<String>

  suspend fun getArtifactIds(groupId: String): Set<String>

  fun getVersionsBlocking(groupId: String, artifactId: String): Set<String>

  suspend fun getVersions(groupId: String, artifactId: String): Set<String>

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<MavenDependencySearchContributor> = ExtensionPointName.create("org.jetbrains.idea.maven.dependencySearchContributor")
  }
}
