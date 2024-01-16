// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.io.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import org.jetbrains.idea.maven.model.RepositoryKind
import org.jetbrains.idea.maven.server.AddArtifactResponse
import org.jetbrains.idea.maven.server.IndexedMavenId
import org.jetbrains.idea.maven.statistics.MavenIndexUsageCollector
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import java.io.File
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MavenLocalGavIndexImpl(val repo: MavenRepositoryInfo) : MavenGAVIndex, MavenUpdatableIndex {

  private val group2Artifacts = ConcurrentHashMap<String, MutableSet<String>>()
  private val mavenIds2Versions = ConcurrentHashMap<String, MutableSet<String>>()
  private val repoFile = Paths.get(repo.url).toFile().canonicalFile

  override fun close(releaseIndexContext: Boolean) {
    mavenIds2Versions.clear()
    group2Artifacts.clear()
  }

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

  override fun updateOrRepair(fullUpdate: Boolean, progress: MavenProgressIndicator, explicit: Boolean) {
    val activity = MavenIndexUsageCollector.GAV_INDEX_UPDATE.started(null);
    var success = false;
    var filesProcessed = 0
    var startTime = System.currentTimeMillis();
    try {
      if (fullUpdate) {
        group2Artifacts.clear()
        mavenIds2Versions.clear()
      }
      runBlockingMaybeCancellable {
        launch(Dispatchers.IO) {
          repoFile.walkBottomUp()
            .filter { it.name.endsWith(".pom") }
            .mapNotNull { extractMavenId(it) }
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
      success = true;
      progress.setText(IndicesBundle.message("maven.indices.updated.for.repo", repo.name))
    }
    finally {
      MavenLog.LOG.info(
        "GAV index updated for repo $repoFile, $filesProcessed files processed in ${group2Artifacts.size} groups in ${System.currentTimeMillis() - startTime} millis")
      activity.finished {
        listOf(
          MavenIndexUsageCollector.MANUAL.with(explicit),
          MavenIndexUsageCollector.IS_SUCCESS.with(success),
          MavenIndexUsageCollector.GROUPS_COUNT.with(group2Artifacts.size),
          MavenIndexUsageCollector.ARTIFACTS_COUNT.with(filesProcessed),
        )
      }

    }

  }

  private fun addTo(map: MutableMap<String, MutableSet<String>>, key: String, value: String) {
    val set = map.computeIfAbsent(key) { ConcurrentHashMap<String, Boolean>().keySet(true) }
    set.add(value)
  }

  private fun extractMavenId(it: File): MavenId? {
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

  override fun tryAddArtifacts(artifactFiles: Collection<File>): List<AddArtifactResponse> {
    val result = ArrayList<AddArtifactResponse>()
    artifactFiles.forEach {
      val file = it.absoluteFile
      if (!FileUtil.isAncestor(repoFile, it, true)) {
        result.add(AddArtifactResponse(file, null))
      }
      else {
        val id = extractMavenId(file)
        if (id == null) {
          result.add(AddArtifactResponse(file, null))
        }
        else {
          addTo(group2Artifacts, id!!.groupId!!, id.artifactId!!)
          addTo(mavenIds2Versions, id2string(id.groupId!!, id.artifactId!!), id.version!!)
          result.add(AddArtifactResponse(file, IndexedMavenId(id.groupId, id.artifactId, id.version, "", "")))
        }
      }
    }
    return result;
  }

}