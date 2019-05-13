// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.ide.BootstrapClassLoaderUtil
import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PlatformUtils
import com.intellij.util.io.ZipUtil
import com.intellij.util.ui.UIUtil
import java.io.File


/**
 * @author Aleksey.Rostovskiy
 */
open class PyGeneratorBase {
  protected val app: IdeaTestApplication by lazy {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, PlatformUtils.PYCHARM_CE_PREFIX)
    System.setProperty(PathManager.PROPERTY_PLUGINS_PATH, FileUtil.createTempDirectory("pystubs", "plugins").absolutePath)
    System.setProperty(PathManager.PROPERTY_SYSTEM_PATH, FileUtil.createTempDirectory("pystubs", "system").absolutePath)
    System.setProperty(PathManager.PROPERTY_CONFIG_PATH, FileUtil.createTempDirectory("pystubs", "config").absolutePath)
    Thread.currentThread().contextClassLoader = BootstrapClassLoaderUtil.initClassLoader()
    IdeaTestApplication.getInstance()
  }

  protected fun rootFiles(root: String): ArrayList<VirtualFile>{
    val dir = File(root)
    if (!dir.exists() || !dir.isDirectory) {
      throw IllegalStateException("$root doesn't exist or isn't a directory")
    }

    return ArrayList(dir.listFiles().map { it->
      LocalFileSystem.getInstance().findFileByIoFile(it)!!
    })
  }

  protected fun unzipArchivesToRoots(root: String): ArrayList<VirtualFile> {
    val dir = File(root)
    if (!dir.exists() || !dir.isDirectory) {
      throw IllegalStateException("$root doesn't exist or isn't a directory")
    }
    val files = dir.listFiles { _, name -> name.endsWith(".zip") }.map { zip ->
      val unzipRoot = File(root, zip.nameWithoutExtension)
      println("Extracting $zip")
      ZipUtil.extract(zip, unzipRoot, null)
      unzipRoot
    }
    return ArrayList(files.map { it ->
      LocalFileSystem.getInstance().findFileByIoFile(it)!!
    })
  }

  protected fun tearDown() {
    UIUtil.invokeAndWaitIfNeeded(Runnable {
      WriteAction.run<Throwable> {
        app.dispose()
      }
    })
    FileUtil.delete(File(System.getProperty(PathManager.PROPERTY_PLUGINS_PATH)))
    FileUtil.delete(File(System.getProperty(PathManager.PROPERTY_SYSTEM_PATH)))
  }
}