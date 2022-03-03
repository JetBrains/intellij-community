package com.jetbrains.python.ifs

import com.intellij.testFramework.PlatformTestUtil
import java.io.File

object PythonSuggestersTestUtils {
  val testDataPath: String
    get() = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/python/python-features-trainer/testData"
}