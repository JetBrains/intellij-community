package com.intellij.mermaid.lang

import com.intellij.testFramework.UsefulTestCase
import java.nio.file.Path
import kotlin.io.path.Path

object MermaidTestingUtil {
  const val TEST_DATA_PATH = "src/test/resources"

  fun obtainTestDataPath(): Path {
    return Path(TEST_DATA_PATH).toAbsolutePath()
  }

  fun getTestName(name: String?, lowercaseFirstLetter: Boolean): String {
    val testName = UsefulTestCase.getTestName(name, lowercaseFirstLetter)
    return testName.trimStart().replace(' ', '_')
  }
}
