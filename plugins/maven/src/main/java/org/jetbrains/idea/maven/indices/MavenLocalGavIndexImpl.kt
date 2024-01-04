// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.model.RepositoryKind
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import java.io.File
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MavenLocalGavIndexImpl(val repo: MavenRepositoryInfo) : MavenGAVIndex, MavenUpdatableIndex {

  val group2Artifacts = ConcurrentHashMap<String, MutableSet<String>>()
  val mavenIds2Versions = ConcurrentHashMap<String, MutableSet<String>>()

  override fun getGroupIds(): Collection<String> = Collections.unmodifiableSet(group2Artifacts.keys)

  override fun getArtifactIds(groupId: String): Set<String> = group2Artifacts[groupId]?.let { Collections.unmodifiableSet(it) }
                                                              ?: emptySet()

  override fun getVersions(groupId: String, artifactId: String): Set<String> = mavenIds2Versions[id2string(groupId, artifactId)]?.let {
    Collections.unmodifiableSet(it)
  } ?: emptySet()

  override fun hasGroupId(groupId: String) = group2Artifacts[groupId] != null

  override fun hasArtifactId(groupId: String, artifactId: String) = mavenIds2Versions[id2string(groupId, artifactId)] != null
  override fun hasVersion(groupId: String, artifactId: String, version: String) = mavenIds2Versions[id2string(groupId,
                                                                                                              artifactId)]?.contains(
    version) ?: false

  private fun id2string(groupId: String, artifactId: String): String = "$groupId:$artifactId"

  override fun getKind() = RepositoryKind.LOCAL

  override fun getRepository() = repo
  override fun updateOrRepair(fullUpdate: Boolean, progress: MavenProgressIndicator, multithreaded: Boolean) {
    if (fullUpdate) {
      group2Artifacts.clear()
      mavenIds2Versions.clear()
    }
    val repoFile = Paths.get(repo.url).toFile().canonicalFile
    var filesProcessed = 0
    runBlockingMaybeCancellable {
      launch(Dispatchers.IO) {
        repoFile.walkBottomUp()
          .filter { it.name.endsWith(".pom") }
          .mapNotNull { extractMavenId(it, repoFile) }
          .forEach { id ->
            addTo(group2Artifacts, id.groupId!!, id.artifactId!!)
            addTo(mavenIds2Versions, id2string(id.groupId!!, id.artifactId!!), id.version!!)
            if (filesProcessed % 100 == 0) {
              progress.setText(IndicesBundle.message("maven.indices.scanned.artifacts", filesProcessed))
            }
            filesProcessed++
          }
      }.join()
    }
    progress.setText(IndicesBundle.message("maven.indices.updated.for.repo", repo.name))
  }

  private fun addTo(map: MutableMap<String, MutableSet<String>>, key: String, value: String) {
    val set = map.computeIfAbsent(key) { ConcurrentHashMap<String, Boolean>().keySet(true) }
    set.add(value)
  }

  private fun extractMavenId(it: File, repoFile: File): MavenId? {
    if (MavenLog.LOG.isTraceEnabled) {
      MavenLog.LOG.trace("extracting id from file $it")
    }
    val version = it.parentFile?.name
    val artifactId = it.parentFile?.parentFile?.name
    var dirFile = it.parentFile?.parentFile

    if (version == null || artifactId == null || dirFile == null) {
      MavenLog.LOG.info("Cannot extract maven id from file $it")
      return null
    }
    val list = ArrayList<String>(2)

    dirFile = dirFile.parentFile
    while (dirFile != null && dirFile.absolutePath != repoFile.absolutePath) {
      list.add(dirFile.name)
      dirFile = dirFile.parentFile
    }
    val groupId = list.reversed().joinToString(separator = ".")
    return MavenId(groupId, artifactId, version)

  }
}