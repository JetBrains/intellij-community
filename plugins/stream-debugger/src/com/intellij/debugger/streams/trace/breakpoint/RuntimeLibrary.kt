// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.PathUtil
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.notExists


/**
 * @author Shumaf Lovpache
 */
object RuntimeLibrary {
  private const val STREAM_DEBUGGER_SUPPORT_LIB_JAR_PATH = "artifacts/stream_debugger_rt/stream-debugger-rt.jar"
  private val LOG = thisLogger()

  private val classLoader by lazy {
    val supportLibraryPath = getSupportLibraryPath()
    if (supportLibraryPath.notExists()) {
      LOG.error("Could not load stream debugger runtime library")
    }

    URLClassLoader(arrayOf(supportLibraryPath.toUri().toURL()))
  }

  private fun getSupportLibraryPath(): Path {
    val classesRoot = PathUtil.getJarPathForClass(RuntimeLibrary::class.java)
    return Path.of(classesRoot)
      .parent
      .parent
      .resolve(STREAM_DEBUGGER_SUPPORT_LIB_JAR_PATH)
  }

  fun getBytecodeLoader(classUri: String): BytecodeFactory = {
    classLoader.getResourceAsStream(classUri)?.readAllBytes()
  }
}