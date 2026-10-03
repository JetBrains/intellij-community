// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.testFramework.performance

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ref.GCUtil
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.types.TypeEvalContextCache
import com.jetbrains.python.psi.types.TypeEvalContextCacheImpl
import com.jetbrains.python.psi.types.TypeEvalContextImpl
import java.lang.management.ManagementFactory
import javax.management.ObjectName

/**
 * Measures the memory that the code insight caches retain: the live heap bytes that each drop of a cache frees.
 *
 * The live bytes come from the class histogram of the JVM, the one of `jcmd <pid> GC.class_histogram`. It runs a full
 * collection first. That collection keeps soft references while the heap has room, and the type context maps and the
 * control flow cache hold their values softly. The first drop drops nothing, so its bytes show the noise and would show
 * a collection that cleared the caches by itself.
 *
 * The drops run in order, so a drop counts only the bytes that the earlier drops left.
 */
class PyCacheMemoryProbe(private val project: Project) {

  /** The bytes that one drop freed, and the classes that freed the most bytes. */
  class Drop(val name: String, val liveBytesBefore: Long, val freedBytes: Long, val topClasses: List<Pair<String, Long>>)

  /** The number of cached type contexts of each kind, and the entries of their caches. */
  fun contextCounts(): Map<String, Long> = runReadActionBlocking {
    val result = LinkedHashMap<String, Long>()
    for ((kind, contexts) in contextCache().cachedContexts.toSortedMap()) {
      result["$kind.contexts"] = contexts.size.toLong()
      for (context in contexts) {
        for ((cache, size) in (context as TypeEvalContextImpl).cacheSizes()) {
          result.merge("$kind.$cache", size.toLong(), Long::plus)
        }
      }
    }
    result
  }

  /** Drops the caches one after the other and returns the bytes that each drop freed. [file] gives the control flow. */
  fun dropAll(file: PyFile): List<Drop> {
    val drops = ArrayList<Drop>()
    // A histogram runs while the previous histogram is still referenced. Take one extra histogram first, so the first
    // drop also has one previous histogram alive.
    var before = liveBytesByClass()
    before = liveBytesByClass()
    fun drop(name: String, action: () -> Unit) {
      action()
      val after = liveBytesByClass()
      val freed = before.keys.associateWith { before.getValue(it) - (after[it] ?: 0) }
      drops += Drop(name, before.getValue(TOTAL), before.getValue(TOTAL) - after.getValue(TOTAL),
                    freed.filter { it.key != TOTAL && it.value > 0 }.entries.sortedByDescending { it.value }.take(8).map { it.toPair() })
      before = after
    }
    drop("nothing") {}
    drop("typeContexts") { runReadActionBlocking { contextCache().clear() } }
    drop("resolveCache") { onEdt { PsiManager.getInstance(project).dropResolveCaches() } }
    drop("controlFlow") {
      runReadActionBlocking {
        (PsiTreeUtil.findChildrenOfType(file, ScopeOwner::class.java) + file).forEach(ControlFlowCache::clear)
      }
    }
    drop("softlyReachable") { GCUtil.tryGcSoftlyReachableObjects() }
    return drops
  }

  fun report(label: String, counts: Map<String, Long>, drops: List<Drop>) {
    val lines = ArrayList<String>()
    lines += "PYMEM $label liveBytesBefore=${drops.first().liveBytesBefore}"
    lines += "PYMEM $label counts " + counts.entries.joinToString(" ") { "${it.key}=${it.value}" }
    for (drop in drops) {
      lines += "PYMEM $label freed ${drop.name} bytes=${drop.freedBytes} top=" +
               drop.topClasses.joinToString(",") { "${it.first.substringAfterLast('.')}:${it.second}" }
    }
    PyPerfProbe.emit(lines)
  }

  private fun contextCache() = project.service<TypeEvalContextCache>() as TypeEvalContextCacheImpl

  private fun onEdt(action: () -> Unit) {
    ApplicationManager.getApplication().invokeAndWait { WriteIntentReadAction.run(action) }
  }

  private companion object {
    const val TOTAL = "<total>"
    val HISTOGRAM_LINE = Regex("""^\s*\d+:\s+\d+\s+(\d+)\s+(\S+)""")
    val TOTAL_LINE = Regex("""^Total\s+\d+\s+(\d+)""")

    /** The live bytes of each class after a full collection, and their sum under [TOTAL]. */
    fun liveBytesByClass(): Map<String, Long> {
      val histogram = ManagementFactory.getPlatformMBeanServer().invoke(
        ObjectName("com.sun.management:type=DiagnosticCommand"), "gcClassHistogram",
        arrayOf<Any>(emptyArray<String>()), arrayOf(Array<String>::class.java.name)) as String
      val result = HashMap<String, Long>()
      for (line in histogram.lineSequence()) {
        HISTOGRAM_LINE.find(line)?.let { result.merge(it.groupValues[2], it.groupValues[1].toLong(), Long::plus) }
        TOTAL_LINE.find(line)?.let { result[TOTAL] = it.groupValues[1].toLong() }
      }
      return result
    }
  }
}
