/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.uast.test.env

import com.intellij.openapi.util.text.StringUtil
import com.intellij.rt.execution.junit.FileComparisonFailure
import junit.framework.TestCase
import java.io.File

private fun String.trimTrailingWhitespacesAndAddNewlineAtEOF(): String =
  this.split('\n').map(String::trimEnd).joinToString(separator = "\n").let { result ->
    if (result.endsWith("\n")) result else result + "\n"
  }

fun assertEqualsToFile(description: String, expected: File, actual: String) {
  if (!expected.exists()) {
    expected.writeText(actual)
    TestCase.fail("File didn't exist. New file was created (${expected.canonicalPath}).")
  }

  val expectedText =
    StringUtil.convertLineSeparators(expected.readText().trim()).trimTrailingWhitespacesAndAddNewlineAtEOF()
  val actualText =
    StringUtil.convertLineSeparators(actual.trim()).trimTrailingWhitespacesAndAddNewlineAtEOF()
  if (expectedText != actualText) {
    throw FileComparisonFailure(description, expectedText, actualText, expected.absolutePath)
  }
}
