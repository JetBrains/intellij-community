// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.stubs.PrebuiltStubsProviderBase
import com.intellij.util.io.ZipUtil
import com.jetbrains.python.psi.impl.stubs.PyPrebuiltStubsProvider
import org.jetbrains.intellij.build.pycharm.PyCharmBuildOptions
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipOutputStream


/**
 * @author Aleksey.Rostovskiy
 */
fun main(args: Array<String>) {
  if (args.size != 2) {
    val zipsDirectory = System.getProperty("intellij.build.pycharm.zips.directory")
    val prebuiltStubsArchive = PyCharmBuildOptions.getPrebuiltStubsArchive()

    if (zipsDirectory.isNullOrBlank() || prebuiltStubsArchive.isNullOrBlank()) {
      throw IllegalArgumentException(
        "Usage: PythonUniversalStubsBuilderKt <input folder with files> <output folder to store universal stubs>"
      )
    }
    PythonUniversalStubsBuilder.generateArchive(zipsDirectory, prebuiltStubsArchive)
  }
  else {
    PythonUniversalStubsBuilder.generateStubs(args[0], "${args[1]}/${PyPrebuiltStubsProvider.NAME}")
  }
}

object PythonUniversalStubsBuilder : PyGeneratorBase() {
  fun generateStubs(root: String, outputPath: String) {
    try {
      app

      val files = rootFiles(root)
      PyStubsGenerator("$outputPath/${PrebuiltStubsProviderBase.SDK_STUBS_STORAGE_NAME}")
        .buildStubsForRoots(files)
    }
    catch (e: Throwable) {
      e.printStackTrace()
    }
    finally {
      tearDown()
    }
  }

  fun generateArchive(zipsDirectory: String, prebuiltStubsArchive: String) {
    val tmpFolder = FileUtil.createTempDirectory("stubs", null)
    tmpFolder.delete()
    tmpFolder.mkdirs()
    tmpFolder.deleteOnExit()

    try {
      app

      val unzippedFiles = unzipArchivesToRoots(zipsDirectory)
      PyStubsGenerator("${tmpFolder.absolutePath}/${PrebuiltStubsProviderBase.SDK_STUBS_STORAGE_NAME}")
        .buildStubsForRoots(unzippedFiles)
    }
    catch (e: Throwable) {
      e.printStackTrace()
    }
    finally {
      tearDown()
    }

    println("Generate archive $prebuiltStubsArchive")
    val archive = File(prebuiltStubsArchive)
    ZipOutputStream(FileOutputStream(archive)).use {
      ZipUtil.addFileOrDirRecursively(it, archive, tmpFolder, PyPrebuiltStubsProvider.NAME, null, null)
    }
  }

}