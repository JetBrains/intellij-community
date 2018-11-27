// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.index.PrebuiltIndexAwareIdIndexer
import com.jetbrains.python.psi.impl.stubs.PyPrebuiltStubsProvider
import org.jetbrains.index.id.IdIndexGenerator


/**
 * @author Aleksey.Rostovskiy
 */
fun main(args: Array<String>) {
  require(args.size == 2) {
    "Usage: IndicesBuilderKt <input folder with files> <output folder to store indices>"
  }
  IndicesBuilder.build(args[0], "${args[1]}/${PyPrebuiltStubsProvider.NAME}")
}

object IndicesBuilder: PyGeneratorBase() {
  fun build(root: String, outputPath: String) {
    try {
      app

      val files = rootFiles(root)
      IdIndexGenerator("$outputPath/${PrebuiltIndexAwareIdIndexer.ID_INDEX_FILE_NAME}")
        .buildIdIndexForRoots(files)
    }
    catch (e: Throwable) {
      e.printStackTrace()
    }
    finally {
      tearDown()
    }
  }
}
