// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion.model

import org.jetbrains.idea.maven.model.MavenCoordinate
import org.jetbrains.idea.reposearch.RepositoryArtifactData

class MavenRepositoryArtifactInfo(private val groupId: String,
                                  private val artifactId: String,
                                  val items: Array<MavenDependencyCompletionItem>) : MavenCoordinate, RepositoryArtifactData {

  constructor(g: String, a: String, versions: Collection<String>) : this(g, a,
                                                                    versions.map { MavenDependencyCompletionItem(g, a, it) }.toTypedArray())

  override fun getGroupId(): String {
    return groupId
  }

  override fun getArtifactId(): String {
    return artifactId
  }

  override fun getVersion(): String? {
    return if (items.size < 1) {
      null
    }
    else items[0].version!!
  }

  override fun getKey(): String {
    return getGroupId() + ":" + getArtifactId()
  }

  override fun toString(): String {
    return "maven(" + groupId + ':' + artifactId + ":" + version + " " + items.size + " total)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MavenRepositoryArtifactInfo

    return key == other.key
  }


  override fun hashCode(): Int {
    return key.hashCode()
  }


}