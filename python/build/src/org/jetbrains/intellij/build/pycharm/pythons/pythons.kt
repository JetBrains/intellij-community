// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm.pythons

class Python internal constructor(val pyenvDefinition: String,
                                  val name: String,
                                  val revision: String,
                                  val packages: List<String>)

object Pythons {
  private val allPythons: ArrayList<Python> = ArrayList()

  @Synchronized
  internal fun createPython(pyenvDefinition: String, name: String, revision: String, packages: List<String>): Python {
    return Python(pyenvDefinition, name, revision, packages).also {
      allPythons.add(it)
    }
  }

  @Synchronized
  fun getPythons(): List<Python> {
    return allPythons.toList()
  }

  val BlackJobLibTenserFlowPython311 = createPython("3.11.7",
                                                    "black-joblib-tenserflow",
                                                    "1",
                                                    listOf("black == 23.1.0", "joblib", "tensorflow"))
  val CondaWithPyTorch = createPython("Miniconda3-latest",
                                      "conda-pytorch",
                                      "1",
                                      listOf("pytorch"))
}