package com.intellij.mermaid.lang

import com.intellij.openapi.application.PluginPathManager
import com.intellij.testFramework.UsefulTestCase
import java.nio.file.Path
import kotlin.io.path.Path

object MermaidTestingUtil {
  val TEST_DATA_PATH: String
    get() = PluginPathManager.getPluginHomePath("mermaid") + "/testData"

  fun obtainTestDataPath(): Path {
    return Path(TEST_DATA_PATH)
  }

  fun getTestName(name: String?, lowercaseFirstLetter: Boolean): String {
    val testName = UsefulTestCase.getTestName(name, lowercaseFirstLetter)
    return testName.trimStart().replace(' ', '_')
  }
}
