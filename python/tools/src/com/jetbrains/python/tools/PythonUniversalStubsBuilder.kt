// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.stubs.PrebuiltStubsProviderBase
import com.intellij.util.io.Compressor
import com.jetbrains.python.psi.impl.stubs.PyPrebuiltStubsProvider
import java.io.File
import kotlin.system.exitProcess

/**
 * @author Aleksey.Rostovskiy
 */
fun main(args: Array<String>) {
  try {
    if (args.size == 2) {
      PythonUniversalStubsBuilder.generateStubs(args[0], "${args[1]}/${PyPrebuiltStubsProvider.NAME}")
    }
    else {
      val zipsDirectory = System.getProperty("intellij.build.pycharm.zips.directory")
      val prebuiltStubsArchive = System.getProperty("intellij.build.pycharm.prebuilt.stubs.archive")
      if (zipsDirectory.isNullOrBlank() || prebuiltStubsArchive.isNullOrBlank()) {
        throw IllegalArgumentException(
          "Usage: PythonUniversalStubsBuilderKt <input folder with files> <output folder to store universal stubs>")
      }
      PythonUniversalStubsBuilder.generateArchive(zipsDirectory, prebuiltStubsArchive)
    }
    exitProcess(0)
  }
  catch (e: Throwable) {
    e.printStackTrace()
    exitProcess(1)
  }
}

private object PythonUniversalStubsBuilder : PyGeneratorBase() {
  fun generateStubs(root: String, outputPath: String) {
    use {
      val files = rootFiles(root)
      PyStubsGenerator("$outputPath/${PrebuiltStubsProviderBase.SDK_STUBS_STORAGE_NAME}")
        .buildStubsForRoots(files)
    }
  }

  fun generateArchive(zipsDirectory: String, prebuiltStubsArchive: String) {
    val stubDir = tempDir.resolve("stubs")
    use {
      val unzippedFiles = unzipArchivesToRoots(zipsDirectory)
      PyStubsGenerator(FileUtil.toSystemIndependentName(stubDir.resolve(PrebuiltStubsProviderBase.SDK_STUBS_STORAGE_NAME).toString()))
        .buildStubsForRoots(unzippedFiles)

      println("Generate archive $prebuiltStubsArchive")
      val archive = File(prebuiltStubsArchive)
      Compressor.Zip(archive).use { it.addDirectory(PyPrebuiltStubsProvider.NAME, stubDir.toFile()) }
    }
  }
}