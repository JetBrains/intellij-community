// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.metaInfo

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.PluginPathManager
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.jvm.optionals.getOrNull

@TestOnly
enum class Repository(internal val contentRootResolver: (String) -> Path) {
  PY_COMMUNITY({ getContentDir(tryAddingCommunity = true, it) }),
  PY_PROFESSIONAL({ getContentDir(tryAddingCommunity = false, it) }),
  PLUGINS({ Path(PluginPathManager.getPluginHomePath(it)) })
}

private fun getContentDir(tryAddingCommunity: Boolean, content: String): Path {
  var path = PathManager.getHomeDirFor(Repository::class.java)!!
  if (tryAddingCommunity) {
    val community = path.resolve("community") // Community might also be a part of the path in community tests
    if (community.exists()) {
      path = community
    }
  }
  return path.resolve("python").resolve(content)
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

  val contentRoot = repository.contentRootResolver(contentRootPath).toString()
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

