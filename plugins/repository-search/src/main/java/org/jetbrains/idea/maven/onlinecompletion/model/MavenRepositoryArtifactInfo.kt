// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion.model

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.maven.model.MavenCoordinate
import org.jetbrains.idea.reposearch.RepositoryArtifactData

class MavenRepositoryArtifactInfo(
  private val groupId: String,
  private val artifactId: String,
  val items: Array<MavenDependencyCompletionItem>
) : MavenCoordinate, RepositoryArtifactData {

  constructor(
    groupId: String,
    artifactId: String,
    versions: Collection<String>
  ) : this(
    groupId = groupId,
    artifactId = artifactId,
    items = versions.map { MavenDependencyCompletionItem(groupId, artifactId, it) }.toTypedArray()
  )

  @NlsSafe
  override fun getGroupId() = groupId

  @NlsSafe
  override fun getArtifactId() = artifactId

  @NlsSafe
  override fun getVersion() = items.firstOrNull()?.version

  @NlsSafe
  override fun getKey() = "$groupId:$artifactId"

  @NonNls
  override fun toString() = "maven($groupId:$artifactId:$version ${items.size} total)"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as MavenRepositoryArtifactInfo
    return key == other.key
  }

  override fun hashCode() = key.hashCode()
}