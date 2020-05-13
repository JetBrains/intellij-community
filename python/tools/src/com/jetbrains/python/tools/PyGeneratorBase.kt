// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.loadHeadlessAppInUnitTestMode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PlatformUtils
import com.intellij.util.io.Decompressor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * @author Aleksey.Rostovskiy
 */
open class PyGeneratorBase : AutoCloseable {
  @Suppress("SpellCheckingInspection")
  protected val tempDir: Path = Files.createTempDirectory("pystubs").toAbsolutePath()

  init {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, PlatformUtils.PYCHARM_CE_PREFIX)

    val dir = FileUtil.toSystemIndependentName(tempDir.toString())
    System.setProperty(PathManager.PROPERTY_PLUGINS_PATH, "$dir/plugins")
    System.setProperty(PathManager.PROPERTY_SYSTEM_PATH, "$dir/system")
    System.setProperty(PathManager.PROPERTY_CONFIG_PATH, "$dir/config")

    loadHeadlessAppInUnitTestMode()
  }

  protected fun rootFiles(root: String): List<VirtualFile> {
    val dir = Paths.get(root)
    return Files.newDirectoryStream(dir).use { children ->
      children.map {
        LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(it.toString()))!!
      }
    }
  }

  protected fun unzipArchivesToRoots(root: String): List<VirtualFile> {
    val rootDir = Paths.get(root)
    return Files.newDirectoryStream(rootDir).use { children ->
      children.mapNotNull { path ->
        val fileName = path.fileName.toString()
        if (!fileName.endsWith(".zip")) {
          return@mapNotNull null
        }

        val unzipRoot = rootDir.resolve(FileUtil.getNameWithoutExtension(fileName))
        println("Extracting $path")
        Decompressor.Zip(path.toFile()).extract(unzipRoot.toFile())
        LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(unzipRoot.toString()))!!
      }
    }
  }

  final override fun close() {
    FileUtil.delete(tempDir)
  }
}