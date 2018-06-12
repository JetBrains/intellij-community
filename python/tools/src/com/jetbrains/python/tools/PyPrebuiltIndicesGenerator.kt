// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.idea.IdeaTestApplication
import com.intellij.index.PrebuiltIndexAwareIdIndexer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.stubs.PrebuiltStubsProviderBase
import com.intellij.testFramework.PlatformTestCase
import com.intellij.util.ReflectionUtil
import com.intellij.util.io.ZipUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.psi.impl.stubs.PyPrebuiltStubsProvider
import org.jetbrains.index.id.IdIndexGenerator
import org.jetbrains.index.stubs.StubGeneratorBootstrap
import org.jetbrains.index.stubs.bootstrapAndRun
import java.io.File
import java.util.*

fun main(args: Array<String>) {
  bootstrapAndRun(args, PyPrebuiltIndicesGeneratorBootstrap::class.java.name)
}

class PyPrebuiltIndicesGeneratorBootstrap : StubGeneratorBootstrap {
  override fun run(args: Array<String>) {
    buildIndices(args[0], "${args[1]}/${PyPrebuiltStubsProvider.NAME}")
  }

  private fun buildIndices(root: String, outputPath: String) {
    val app = createApp()
    try {
      FileUtil.delete(File(outputPath))

      val roots = unzipArchivesToRoots(root)

      val rootFiles = ArrayList(roots.map { it -> LocalFileSystem.getInstance().findFileByIoFile(it)!! })

      PyStubsGenerator("$outputPath/${PrebuiltStubsProviderBase.SDK_STUBS_STORAGE_NAME}").buildStubsForRoots(rootFiles)

      IdIndexGenerator("$outputPath/${PrebuiltIndexAwareIdIndexer.ID_INDEX_FILE_NAME}").buildIdIndexForRoots(rootFiles)
    }
    catch (e: Throwable) {
      e.printStackTrace()
    }
    finally {
      UIUtil.invokeAndWaitIfNeeded(Runnable {
        WriteAction.run<Throwable> {
          app.dispose()
        }
      })
      FileUtil.delete(File(System.getProperty(PathManager.PROPERTY_PLUGINS_PATH)))
      FileUtil.delete(File(System.getProperty(PathManager.PROPERTY_SYSTEM_PATH)))
      System.exit(0)
    }
  }

  private fun unzipArchivesToRoots(root: String): List<File> {
    val dir = File(root)
    if (!dir.exists() || !dir.isDirectory) {
      throw IllegalStateException("$root doesn't exist or isn't a directory")
    }
    return dir.listFiles { _, name -> name.endsWith(".zip") }.map { zip ->
      val unzipRoot = File(root, zip.nameWithoutExtension)
      println("Extracting $zip")
      ZipUtil.extract(zip, unzipRoot, null)
      unzipRoot
    }
  }

  private fun createApp(): IdeaTestApplication {
    val candidates = ReflectionUtil.getField(PlatformTestCase::class.java, null, Array<String>::class.java, "PREFIX_CANDIDATES")
    candidates[0] = "PyCharm"
    System.setProperty(PathManager.PROPERTY_PLUGINS_PATH, FileUtil.createTempDirectory("pystubs", "plugins").absolutePath)
    System.setProperty(PathManager.PROPERTY_SYSTEM_PATH, FileUtil.createTempDirectory("pystubs", "system").absolutePath)
    System.setProperty(PathManager.PROPERTY_CONFIG_PATH, FileUtil.createTempDirectory("pystubs", "config").absolutePath)
    return IdeaTestApplication.getInstance()
  }
}
