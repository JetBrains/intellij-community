// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.psi.stubs.StubUpdatingIndex
import com.jetbrains.python.psi.PyFileElementType


/**
 * This script is needed for PyCharm installation process to check indexer version of pre-built stubs
 */
fun main() {
  println("${StubUpdatingIndex().version}\n${PyFileElementType.INSTANCE.stubVersion}")
}
