package com.intellij.python.junit5Tests.framework

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.jvm.optionals.getOrNull

/**
 * Reports processes created after the test start and still running at the end of the test.
 * `WARN` log level is used
 */
class LeakedProcessReporterExtension(private val logger: Logger = fileLogger()) : BeforeEachCallback, AfterEachCallback {
  private companion object {
    const val KEY = "processesBeforeStart"

    fun getProcesses(): Map<Long, String> = ProcessHandle.allProcesses()
      .toList()
      .associate { it.pid() to it.info().command().getOrNull() }
      .mapNotNull { (key, value) -> value?.let { key to it } }
      .toMap()
  }

  override fun beforeEach(context: ExtensionContext) {
    context.getStore(ExtensionContext.Namespace.GLOBAL).put(KEY, getProcesses())
  }

  @Suppress("UNCHECKED_CAST")
  override fun afterEach(context: ExtensionContext) {
    val before: Map<Long, String> = context.getStore(ExtensionContext.Namespace.GLOBAL).get(KEY, Map::class.java) as Map<Long, String>
    val after = getProcesses()

    val leakedPids = after.keys - before.keys
    if (leakedPids.isNotEmpty()) {
      val leakedProcesses = after.filter { (pid, _) -> pid in leakedPids }.toMap()
      logger.warn("Following processes leaked: ${leakedProcesses}")
    }
  }
}