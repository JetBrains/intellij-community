// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm.pythons

class Python internal constructor(val pyenvDefinition: String,
                                  val name: String,
                                  val revision: String,
                                  val packages: List<String>)

object Pythons {
  private val allPythons: ArrayList<Python> = ArrayList()

  @Synchronized
  fun getPythons(): List<Python> {
    return allPythons.toList()
  }

}