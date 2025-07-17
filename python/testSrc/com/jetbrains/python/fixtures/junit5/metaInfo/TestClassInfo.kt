// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures.junit5.metaInfo

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.PluginPathManager
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.jvm.optionals.getOrNull

@TestOnly
enum class Repository(val contentRootResolver: (String) -> String) {
  PY_COMMUNITY({ "${PathManager.getHomePath()}/community/python/${it}" }),
  PY_PROFESSIONAL({ "${PathManager.getHomePath()}/python/${it}" }),
  PLUGINS({ PluginPathManager.getPluginHomePath(it) })
}

/**
 * Annotation for defining $CONTENT_ROOT variable in test classes runtime.
 *
 * @property repository Specifies the repository where the test content resides. .
 * @property contentRootPath Specifies the path in the [repository] which will be used as $CONTENT_ROOT.
 */
@TestOnly
annotation class TestClassInfo(
  val repository: Repository = Repository.PY_COMMUNITY,
  val contentRootPath: String = "testSrc",
)

internal fun TestClassInfo.resolvePath(pathWithPlaceholders: String): Path {
  val contentRootPlaceholder = $$"$CONTENT_ROOT"

  if (!pathWithPlaceholders.contains(contentRootPlaceholder)) {
    return Path.of(pathWithPlaceholders)
  }

  val contentRoot = repository.contentRootResolver(contentRootPath)
  val testDataPath = pathWithPlaceholders.replace(contentRootPlaceholder, contentRoot)

  return Path.of(testDataPath)
}

@TestOnly
data class TestClassInfoData(val testDataPath: Path?) {
  fun getTestResourcePath(fileName: String): Path? {
    val resources = testDataPath?.listDirectoryEntries("${fileName}{,.*}") ?: return null
    return when (resources.size) {
      0 -> null
      1 -> resources.first()
      else -> error("Multiple resources found for $fileName: ${resources.joinToString(", ")}")
    }
  }
}

@TestOnly
data class TestMethodInfoData(val testCaseFilePath: Path?)


internal fun <T : Annotation> getAnnotation(context: ExtensionContext?, clazz: Class<T>): T? {
  return context?.testClass?.map { element -> element.getAnnotation(clazz) }?.getOrNull()
}

