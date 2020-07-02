// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.asIdentifiers
import org.jetbrains.uast.test.common.asRefNames
import com.intellij.testFramework.assertEqualsToFile
import java.io.File

abstract class AbstractJavaIdentifiersTest : AbstractJavaUastTest() {
  private fun getTestFile(testName: String, ext: String) =
    File(testDataPath, testName.substringBeforeLast('.') + '.' + ext)

  override fun check(testName: String, file: UFile) {
    assertEqualsToFile("Identifiers", getTestFile(testName, "identifiers.txt"), file.asIdentifiers())
    assertEqualsToFile("refNames", getTestFile(testName, "refNames.txt"), file.asRefNames())
  }
}