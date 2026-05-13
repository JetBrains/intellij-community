// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.metaInfo

import com.intellij.python.junit5Tests.framework.TestResourcePathResolver
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.platform.commons.support.AnnotationSupport
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
class TestMetaInfoExtension : BeforeAllCallback, BeforeEachCallback, Extension, ParameterResolver {
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
    val testDataPathWithPlaceholders = getAnnotation(context, TestDataPath::class.java)?.value

    val testDataPath = testDataPathWithPlaceholders?.let { pathWithPlaceholders ->
      val testClassInfo = getAnnotation(context, TestClassInfo::class.java)
                          ?: error("Add ${TestClassInfo::class} class level")

      testClassInfo.resolvePath(pathWithPlaceholders)
    }

    val data = TestClassInfoData(
      testDataPath = testDataPath,
    )
    context.setTestClassInfo(data)
  }

  override fun beforeEach(context: ExtensionContext) {
    val explicitPath = context.getTestCaseFilePath()

    val testCaseFilePath = if (explicitPath != null) {
      explicitPath
    }
    else {
      val testClassInfo = context.getTestClassInfo()
      val testMethod = context.testMethod.get()
      val testMetaInfo = AnnotationSupport
        .findAnnotation(testMethod, TestMetaInfo::class.java)
        .getOrNull()

      if (testMetaInfo != null && testClassInfo.testDataPath == null) {
        error(
          "@TestMetaInfo on ${testMethod.declaringClass.name}#${testMethod.name} " +
          "requires @TestDataPath on the test class to resolve '${testMetaInfo.resourcePath}'."
        )
      }

      testClassInfo.testDataPath?.let { testDataPath ->
        val testName = context.resolveTestName()

        if (testMetaInfo != null) {
          val resourceRaw = testMetaInfo.resourcePath
          val resolvers = getCustomResolversOtherwiseDefault(context)
          val substitutedPath = resolvers.fold(resourceRaw) { acc, r ->
            r.resolve(acc, context, testName, testClassInfo)
          }

          val resolved = testDataPath.resolve(substitutedPath)
                         ?: error("Test file $substitutedPath not found under $testDataPath")
          resolved.relativeTo(testDataPath)
        }
        else {
          testClassInfo.getTestResourcePath(testName)?.relativeTo(testDataPath)
        }
      }
    }

    val data = TestMethodInfoData(testCaseRelativePath = testCaseFilePath)
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

  private fun getCustomResolversOtherwiseDefault(context: ExtensionContext): List<TestResourcePathResolver> {
    val testClass = context.testClass.orElse(null)
    val testMethod = context.testMethod.orElse(null)

    val chain = mutableListOf<TestResourcePathResolver>()

    fun addResolver(anno: WithCustomTestResourcePathResolver?) {
      anno?.let {
        val constructor = it.value.java.getDeclaredConstructor()
        constructor.isAccessible = true
        chain += constructor.newInstance() as TestResourcePathResolver
      }
    }

    addResolver(testClass?.let {
      AnnotationSupport.findAnnotation(it, WithCustomTestResourcePathResolver::class.java).getOrNull()
    })
    addResolver(testMethod?.let {
      AnnotationSupport.findAnnotation(it, WithCustomTestResourcePathResolver::class.java).getOrNull()
    })

    if (chain.isEmpty()) {
      chain += DefaultTestNameResolver
    }

    return chain
  }
}

internal object DefaultTestNameResolver : TestResourcePathResolver {
  private const val TEST_NAME_TOKEN = $$"$TEST_NAME"

  override fun resolve(
    resourcePath: String,
    context: ExtensionContext,
    testName: String,
    classInfo: TestClassInfoData,
  ): String = resourcePath.replace(TEST_NAME_TOKEN, testName)
}
