// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion.model

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.maven.model.MavenDependencyCompletionItem
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import org.jetbrains.idea.reposearch.RepositoryArtifactData

class MavenRepositoryArtifactInfo(
  groupId: String,
  artifactId: String,
  items: Array<MavenDependencyCompletionItem>
) : MavenRepoArtifactInfo(groupId, artifactId, items), RepositoryArtifactData {

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
  override fun getGroupId() = super.getGroupId()

  @NlsSafe
  override fun getArtifactId() = super.getArtifactId()

  @NlsSafe
  override fun getVersion() = super.getVersion()

  @NlsSafe
  override fun getKey() = "$groupId:$artifactId"
  override fun mergeWith(another: RepositoryArtifactData): MavenRepositoryArtifactInfo {
    if (another !is MavenRepositoryArtifactInfo) {
      throw IllegalArgumentException()
    }
    return MavenRepositoryArtifactInfo(groupId, artifactId, items + another.items);
  }

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