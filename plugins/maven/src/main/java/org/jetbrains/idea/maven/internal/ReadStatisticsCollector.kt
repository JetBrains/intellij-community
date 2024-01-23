// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.internal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.server.DummyMavenServerConnector.Companion.isDummy
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.server.MavenServerStatus
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Service
class ReadStatisticsCollector {
  private fun shouldCollectMavenStatistics() = Registry.`is`("maven.collect.local.stat")
  private val map = ConcurrentHashMap<String, AtomicInteger>()


  fun fileRead(file: VirtualFile) {
    if (!shouldCollectMavenStatistics()) return
    fileRead(file.path)
  }

  fun fileRead(file: File) {
    if (!shouldCollectMavenStatistics()) return
    fileRead(file.absolutePath)
  }

  private fun fileRead(path: String) {
    val counter = map.get(path)?.also { it.incrementAndGet() }
    if (counter == null) {
      map.putIfAbsent(path, AtomicInteger(1))?.incrementAndGet()
    }
  }

  fun print() {

    val totalMap = map.mapValues { it.value.get() }.toMutableMap()

    println("======================================================")
    println("Maven files read Statistics IDEA process: ")

    printAsArray(totalMap)

    println("======================================================")
    println("getting info from maven server:")

    val readMapFromMaven = HashMap<String, Int>()
    val resolvePluginsFromMaven = HashMap<String, Int>()
    getDebugStatusFromMaven(false)
      .forEach {
        readMapFromMaven.mergeWith(it.fileReadAccessCount)
        resolvePluginsFromMaven.mergeWith(it.pluginResolveCount)
      }

    printAsArray(readMapFromMaven)

    println("======================================================")
    println("total: ")

    totalMap.mergeWith(readMapFromMaven)

    printAsArray(totalMap)

    println("======================================================")
    println("plugins:")

    printAsArray(resolvePluginsFromMaven)
    println("======================================================")

    map.clear()
  }

  private fun getDebugStatusFromMaven(clean: Boolean): List<MavenServerStatus> = MavenServerManager.getInstance().allConnectors
    .asSequence()
    .filter { !it.isDummy() }
    .mapSafe { it.getDebugStatus(clean) }
    .filter { it.statusCollected }
    .toList()

  fun reset() {
    map.clear();
    getDebugStatusFromMaven(true);

  }


  companion object {
    @JvmStatic
    fun getInstance(): ReadStatisticsCollector = ApplicationManager.getApplication().service()

  }
}

private fun MutableMap<String, Int>.mergeWith(map: Map<String, Int>) {
  map.entries.forEach {
    this.merge(it.key, it.value) { o, n -> o + n }
  }

}


private fun <C : Comparable<C>> printAsArray(readMapFromMaven: Map<String, C>) {
  val arrayFromMaven = ArrayList(readMapFromMaven.entries)

  arrayFromMaven.sortedByDescending { it.value }.forEach {
    println("${it.value} \t\t ${it.key}")
  }
}

private inline fun <T, R> Sequence<T>.mapSafe(crossinline transform: (T) -> R): Sequence<R> {
  return this.mapNotNull {
    try {
      return@mapNotNull transform(it)
    }
    catch (_: Throwable) {
      return@mapNotNull null
    }
  }
}