// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures.junit5.metaInfo

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.nio.file.Path
import kotlin.io.path.relativeTo
import kotlin.jvm.optionals.getOrNull

internal fun ExtensionContext.resolveTestName(): String {
  return testMethod.map { PlatformTestUtil.getTestName(it.name, true) }.getOrNull()
         ?: error("Can't resolve test name for ${testMethod.map { it.name }}")
}

/**
 * A JUnit 5 extension that provides test metadata management capabilities at both class and method levels.
 *
 * Features of the extension include:
 * - Resolves and processes class-level and method-level test data paths using custom annotations.
 * - Injects [TestClassInfoData] and [TestMethodInfoData] as parameters into test methods for easy use.
 * - Supports deriving test resource paths based on naming conventions and annotations.
 */
internal class TestMetaInfoExtension : BeforeAllCallback, BeforeEachCallback, Extension, ParameterResolver {
  companion object {
    fun ExtensionContext.getTestClassInfo(): TestClassInfoData {
      val store = getStore(Namespace.GLOBAL)
      return store.get(TestClassInfoData::class.java) as? TestClassInfoData
             ?: error("${TestClassInfoData::class.java} is not found / not valid in the context")
    }

    private fun ExtensionContext.setTestClassInfo(testClassInfo: TestClassInfoData) {
      val store = getStore(Namespace.GLOBAL)
      return store.put(TestClassInfoData::class.java, testClassInfo)
    }

    fun ExtensionContext.getTestMethodInfo(): TestMethodInfoData {
      val store = getStore(Namespace.GLOBAL)
      return store.get(TestMethodInfoData::class.java) as? TestMethodInfoData
             ?: error("${TestMethodInfoData::class.java} is not found / not valid in the context")
    }

    private fun ExtensionContext.setTestMethodInfo(testMethodInfo: TestMethodInfoData) {
      val store = getStore(Namespace.GLOBAL)
      return store.put(TestMethodInfoData::class.java, testMethodInfo)
    }

    fun ExtensionContext.setTestCaseFilePath(path: Path) {
      val store = getStore(Namespace.GLOBAL)
      return store.put(Path::class.java, path)
    }

    private fun ExtensionContext.getTestCaseFilePath(): Path? {
      val store = getStore(Namespace.GLOBAL)
      return store.get(Path::class.java) as? Path
    }
  }

  /**
   * Class level initialization.
   * Calculates a real test data path based on class annotations (resolves $CONTENT_ROOT placeholder).
   */
  override fun beforeAll(context: ExtensionContext) {
    val testClassInfo = getAnnotation(context, TestClassInfo::class.java)
                        ?: error("Add ${TestClassInfo::class} class level")

    val testDataPathWithPlaceholders = getAnnotation(context, TestDataPath::class.java)?.value
    val testDataPath = testDataPathWithPlaceholders?.let { testClassInfo.resolvePath(it) }

    val data = TestClassInfoData(
      testDataPath = testDataPath,
    )
    context.setTestClassInfo(data)
  }

  override fun beforeEach(context: ExtensionContext) {
    val explicitPath = context.getTestCaseFilePath()

    val testCaseFilePath = if (explicitPath != null) explicitPath
    else {
      val testClassInfo = context.getTestClassInfo()
      testClassInfo.testDataPath?.let { testDataPath ->
        val testName = context.resolveTestName()
        testClassInfo.getTestResourcePath(testName)?.relativeTo(testDataPath)
      }
    }

    val data = TestMethodInfoData(
      testCaseFilePath = testCaseFilePath
    )
    context.setTestMethodInfo(data)
  }

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
    return when (parameterContext.parameter.type) {
      TestClassInfoData::class.java,
      TestMethodInfoData::class.java,
        -> true
      else -> false
    }
  }

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
    when (parameterContext.parameter.type) {
      TestClassInfoData::class.java -> extensionContext.getTestClassInfo()
      TestMethodInfoData::class.java -> extensionContext.getTestMethodInfo()
      else -> null
    }?.also { return it }

    error("Not supported parameter received ${parameterContext.parameter.type}")
  }
}
