// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.openapi.util.io.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import org.jetbrains.idea.maven.server.AddArtifactResponse
import org.jetbrains.idea.maven.server.IndexedMavenId
import org.jetbrains.idea.maven.statistics.MavenIndexUsageCollector
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.name
import kotlin.io.path.pathString

private fun Path.walkBottomUp(action: (Path) -> Unit) {
  Files.walkFileTree(this, object : SimpleFileVisitor<Path>() {
    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      action(file)
      return FileVisitResult.CONTINUE
    }

    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
      action(dir)
      return FileVisitResult.CONTINUE
    }
  })
}

class MavenLocalGavIndexImpl(val repo: MavenRepositoryInfo) : MavenGAVIndex, MavenUpdatableIndex {

  private val group2Artifacts = ConcurrentHashMap<String, MutableSet<String>>()
  private val mavenIds2Versions = ConcurrentHashMap<String, MutableSet<String>>()
  private val repoFile = Paths.get(repo.url)
  private val mutex = Mutex();

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

  override fun getRepository() = repo

  override suspend fun update(indicator: MavenProgressIndicator, explicit: Boolean) {
    val activity = MavenIndexUsageCollector.GAV_INDEX_UPDATE.started(null)
    var success = false
    var filesProcessed = 0
    val startTime = System.currentTimeMillis()
    if(mutex.tryLock()){
      try {
        withContext(Dispatchers.IO) {
          try {
            repoFile.walkBottomUp {
              if (it.name.endsWith(".pom")) {
                val id = extractMavenId(it)

                if (id != null) {
                  addTo(group2Artifacts, id.groupId!!, id.artifactId!!)
                  addTo(mavenIds2Versions, id2string(id.groupId!!, id.artifactId!!), id.version!!)
                  if (filesProcessed % 100 == 0) {
                    indicator.setText(IndicesBundle.message("maven.indices.scanned.artifacts", filesProcessed))
                  }
                  if (!isActive) throw MavenProcessCanceledException()
                  filesProcessed++
                }
              }
            }
            success = true
            indicator.setText(IndicesBundle.message("maven.indices.updated.for.repo", repo.name))
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
      } finally {
        mutex.unlock()
      }
    } else {
      MavenLog.LOG.info("maven index updating already")
    }

  }


  private fun addTo(map: MutableMap<String, MutableSet<String>>, key: String, value: String) {
    val set = map.computeIfAbsent(key) { ConcurrentHashMap<String, Boolean>().keySet(true) }
    set.add(value)
  }

  private fun extractMavenId(it: Path): MavenId? {
    if (MavenLog.LOG.isTraceEnabled) {
      MavenLog.LOG.trace("extracting id from file $it")
    }
    val version = it.parent?.fileName?.pathString
    val artifactId = it.parent?.parent?.fileName?.pathString
    var dirFile = it.parent?.parent

    if (version == null || artifactId == null || dirFile == null) {
      MavenLog.LOG.info("Cannot extract maven id from file $it")
      return null
    }
    val list = ArrayList<String>(2)

    dirFile = dirFile.parent
    while (dirFile != null && dirFile != repoFile) {
      list.add(dirFile.name)
      dirFile = dirFile.parent
    }
    val groupId = list.reversed().joinToString(separator = ".")
    return MavenId(groupId, artifactId, version)

  }

  override fun tryAddArtifacts(artifactFiles: Collection<File>): List<AddArtifactResponse> {
    val result = ArrayList<AddArtifactResponse>()
    artifactFiles.forEach {
      val file = it.absoluteFile
      if (!FileUtil.isAncestor(repoFile.pathString, it.path, true)) {
        result.add(AddArtifactResponse(file, null))
      }
      else {
        val id = extractMavenId(file.toPath())
        if (id == null) {
          result.add(AddArtifactResponse(file, null))
        }
        else {
          addTo(group2Artifacts, id.groupId!!, id.artifactId!!)
          addTo(mavenIds2Versions, id2string(id.groupId!!, id.artifactId!!), id.version!!)
          result.add(AddArtifactResponse(file, IndexedMavenId(id.groupId, id.artifactId, id.version, "", "")))
        }
      }
    }
    return result
  }

}