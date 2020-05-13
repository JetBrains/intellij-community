// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.index.PrebuiltIndexAwareIdIndexer
import com.jetbrains.python.psi.impl.stubs.PyPrebuiltStubsProvider
import org.jetbrains.index.id.IdIndexGenerator
import kotlin.system.exitProcess

/**
 * @author Aleksey.Rostovskiy
 */
fun main(args: Array<String>) {
  try {
    if (args.size != 2) {
      println("Usage: IndicesBuilderKt <input folder with files> <output folder to store indices>")
      exitProcess(1)
    }

    IndicesBuilder.build(args[0], "${args[1]}/${PyPrebuiltStubsProvider.NAME}")
  }
  catch (e: Throwable) {
    e.printStackTrace()
    exitProcess(1)
  }
}

private object IndicesBuilder : PyGeneratorBase() {
  fun build(root: String, outputPath: String) {
    use {
      val files = rootFiles(root)
      IdIndexGenerator("$outputPath/${PrebuiltIndexAwareIdIndexer.ID_INDEX_FILE_NAME}").buildIdIndexForRoots(files)
      exitProcess(0)
    }
  }
}
