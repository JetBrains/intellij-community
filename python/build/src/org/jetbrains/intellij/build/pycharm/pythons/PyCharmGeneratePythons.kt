// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm.pythons

import kotlinx.coroutines.runBlocking


// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
object PyCharmGeneratePythons {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking {
      Pythons.getPythons().forEach { it.build() }
    }
  }
}