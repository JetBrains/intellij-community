// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree

import com.intellij.maven.testFramework.MavenTestCase
import junit.framework.TestCase
import org.jetbrains.idea.maven.importing.workspaceModel.ContentRootCollector
import org.jetbrains.idea.maven.importing.workspaceModel.ContentRootDataHolder
import org.jetbrains.idea.maven.importing.workspaceModel.GeneratedFoldersHolder
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.Assert
import org.junit.Test

class ContentRootCollectorTest : MavenTestCase() {

  @Test
  fun `test simple content root`() {
    val baseContentRoot = "/home/a/b/c/maven"
    val sourceMain = "/home/a/b/c/maven/src/main/java"
    val resourceMain = "/home/a/b/c/maven/src/main/resources"
    val sourceTest = "/home/a/b/c/maven/src/test/java"
    val target = "/home/a/b/c/maven/target"
    val generatedSourceFolder = "/home/a/b/c/maven/target/generated-sources/java"
    val generatedTestSourceFolder = "/home/a/b/c/maven/target/generated-sources-test/java"
    val annotationProcessorDirectory = "/home/a/b/c/maven/target/annotation-processor/java"
    val annotationProcessorTestDirectory = "/home/a/b/c/maven/target/annotation-processor-test/java"

    val contentRoots = ContentRootCollector.collect(
      baseContentRoot,
      sourceFolders = mapOf(
        sourceMain to JavaSourceRootType.SOURCE,
        resourceMain to JavaResourceRootType.RESOURCE,
        sourceTest to JavaSourceRootType.TEST_SOURCE,
      ),
      excludeFolders = listOf(target),
      doNotRegisterSourcesUnder = emptyList(),
      generatedFoldersHolder = GeneratedFoldersHolder(annotationProcessorDirectory, annotationProcessorTestDirectory,
                                                      generatedSourceFolder, generatedTestSourceFolder))

    Assert.assertEquals(1, contentRoots.size)
    assertContentRoot(contentRoots,
                      expectedPath = baseContentRoot,
                      expectedSourceFolders = listOf(sourceMain, resourceMain, sourceTest),
                      expectedAnnotationFolders = listOf(annotationProcessorDirectory, annotationProcessorTestDirectory),
                      expectedGeneratedFolders = listOf(generatedSourceFolder, generatedTestSourceFolder),
                      expectedExcludes = listOf(target))
  }

  @Test
  fun `test multiple content roots and empty exclude root and generated sources`() {
    val baseContentRoot = "/home/a/b/c/maven/src/main"
    val sourceMain = "/home/a/b/c/maven/src/main/java"
    val sourceMain2 = "/home/a/b/c/maven/java2"
    val target = "/home/a/b/c/maven/target"
    val generatedSourceFolder = "/home/a/b/c/maven/target/generated-sources/java"
    val generatedTestSourceFolder = "/home/a/b/c/maven/target/generated-sources-test/java"

    val contentRoots = ContentRootCollector.collect(
      baseContentRoot,
      sourceFolders = mapOf(
        sourceMain to JavaSourceRootType.SOURCE,
        sourceMain2 to JavaSourceRootType.SOURCE
      ),
      excludeFolders = listOf(target),
      doNotRegisterSourcesUnder = emptyList(),
      generatedFoldersHolder = GeneratedFoldersHolder(null, null, generatedSourceFolder, generatedTestSourceFolder))

    assertContentRoot(contentRoots, expectedPath = baseContentRoot, expectedSourceFolders = listOf(sourceMain))
    assertContentRoot(contentRoots, expectedPath = sourceMain2, expectedSourceFolders = listOf(sourceMain2))
    assertContentRoot(contentRoots, expectedPath = generatedSourceFolder, expectedGeneratedFolders = listOf(generatedSourceFolder))
    assertContentRoot(contentRoots, expectedPath = generatedTestSourceFolder, expectedGeneratedFolders = listOf(generatedTestSourceFolder))
    Assert.assertEquals(4, contentRoots.size)
  }

  @Test
  fun `test do not register sources under`() {
    val contentRoots = ContentRootCollector.collect(
      "/home",
      sourceFolders = mapOf("/home/src" to JavaSourceRootType.SOURCE,
                            "/home/exclude1/src" to JavaSourceRootType.SOURCE),
      excludeFolders = emptyList(),
      doNotRegisterSourcesUnder = listOf("/home/exclude1",
                                         "/home/exclude2",
                                         "/home/exclude3",
                                         "/home/exclude4",
                                         "/home/exclude5",
                                         "/home/exclude6"),
      generatedFoldersHolder = GeneratedFoldersHolder("/home/exclude2/annotations",
                                                      "/home/exclude3/annotations-test",
                                                      "/home/exclude4/generated",
                                                      "/home/exclude5/generated-test"
      ))

    Assert.assertEquals(1, contentRoots.size)
    assertContentRoot(contentRoots,
                      expectedPath = "/home",
                      expectedSourceFolders = listOf("/home/src"),
                      expectedAnnotationFolders = listOf(),
                      expectedGeneratedFolders = listOf(),
                      expectedExcludes = listOf("/home/exclude1",
                                                "/home/exclude2",
                                                "/home/exclude3",
                                                "/home/exclude4",
                                                "/home/exclude5",
                                                "/home/exclude6"))
  }

  @Test
  fun `test exclude folders`() {
    val contentRoots = ContentRootCollector.collect(
      "/home",
      sourceFolders = mapOf("/home/src" to JavaSourceRootType.SOURCE,
                            "/home/exclude1/src" to JavaSourceRootType.SOURCE,
                            "/home/exclude6" to JavaSourceRootType.SOURCE),

      excludeFolders = listOf("/home/exclude1",
                              "/home/exclude2",
                              "/home/exclude3",
                              "/home/exclude4",
                              "/home/exclude5",
                              "/home/exclude6",
                              "/home/exclude7"),

      doNotRegisterSourcesUnder = emptyList(),
      generatedFoldersHolder = GeneratedFoldersHolder("/home/exclude2/annotations",
                                                      "/home/exclude3/annotations-test",
                                                      "/home/exclude4/generated",
                                                      "/home/exclude5/generated-test"
      ))

    Assert.assertEquals(1, contentRoots.size)
    assertContentRoot(contentRoots,
                      expectedPath = "/home",
                      expectedSourceFolders = listOf("/home/exclude1/src", "/home/exclude6", "/home/src"),
                      expectedAnnotationFolders = listOf("/home/exclude2/annotations", "/home/exclude3/annotations-test"),
                      expectedGeneratedFolders = listOf("/home/exclude4/generated", "/home/exclude5/generated-test"),
                      expectedExcludes = listOf("/home/exclude1",
                                                "/home/exclude2",
                                                "/home/exclude3",
                                                "/home/exclude4",
                                                "/home/exclude5",
                                                "/home/exclude6",
                                                "/home/exclude7"))
  }

  @Test
  fun `test that target root created if needed`() {
    val baseContentRoot = "/home/a/b/c/maven"
    val target = "/home/a/b/c/maven/target"

    val contentRoots = ContentRootCollector.collect(
      baseContentRoot,
      emptyMap(),
      listOf(target),
      emptyList(),
      GeneratedFoldersHolder(null, null, null, null)
    )

    Assert.assertEquals(1, contentRoots.size)
    assertContentRoot(contentRoots,
                      expectedPath = baseContentRoot,
                      expectedExcludes = listOf(target))
  }

  @Test
  fun `test multiple source and resources content roots and annotation processor sources`() {
    val baseContentRoot = "/home/a/b/c/maven/src/main"
    val sourceMain = "/home/a/b/c/maven/src/main/java"
    val sourceMain2 = "/home/a/b/c/maven/java2"
    val sourceMain3 = "/home/a/b/c/maven/main/java3"
    val sourceMain4 = "/home/a/b/c/other/java4"

    val resourceMain = "/home/a/b/c/maven/src/main/resource"
    val resourceMain2 = "/home/a/b/c/maven/resource2"
    val resourceMain3 = "/home/a/b/c/maven/main/resource3"
    val resourceMain4 = "/home/a/b/c/other/resource4"

    val annotationProcessorDirectory = "/home/a/b/c/maven/target/annotation-processor/java"
    val annotationProcessorTestDirectory = "/home/a/b/c/maven/target/annotation-processor-test/java"

    val contentRoots = ContentRootCollector.collect(
      baseContentRoot,
      mapOf(
        sourceMain4 to JavaSourceRootType.SOURCE,
        sourceMain3 to JavaSourceRootType.SOURCE,
        sourceMain2 to JavaSourceRootType.SOURCE,
        sourceMain to JavaSourceRootType.SOURCE,
        resourceMain4 to JavaResourceRootType.RESOURCE,
        resourceMain3 to JavaResourceRootType.RESOURCE,
        resourceMain2 to JavaResourceRootType.RESOURCE,
        resourceMain to JavaResourceRootType.RESOURCE,

        ),
      emptyList(),
      emptyList(),
      GeneratedFoldersHolder(annotationProcessorDirectory, annotationProcessorTestDirectory, null, null))

    assertContentRoot(contentRoots, expectedPath = baseContentRoot, expectedSourceFolders = listOf(sourceMain, resourceMain))
    assertContentRoot(contentRoots, expectedPath = sourceMain2, expectedSourceFolders = listOf(sourceMain2))
    assertContentRoot(contentRoots, expectedPath = sourceMain3, expectedSourceFolders = listOf(sourceMain3))
    assertContentRoot(contentRoots, expectedPath = sourceMain4, expectedSourceFolders = listOf(sourceMain4))

    assertContentRoot(contentRoots, expectedPath = resourceMain2, expectedSourceFolders = listOf(resourceMain2))
    assertContentRoot(contentRoots, expectedPath = resourceMain3, expectedSourceFolders = listOf(resourceMain3))
    assertContentRoot(contentRoots, expectedPath = resourceMain3, expectedSourceFolders = listOf(resourceMain3))

    assertContentRoot(contentRoots, expectedPath = annotationProcessorDirectory,
                      expectedAnnotationFolders = listOf(annotationProcessorDirectory))
    assertContentRoot(contentRoots, expectedPath = annotationProcessorTestDirectory,
                      expectedAnnotationFolders = listOf(annotationProcessorTestDirectory))

    Assert.assertEquals(9, contentRoots.size)
  }

  private fun assertContentRoot(roots: Collection<ContentRootDataHolder>,
                                expectedPath: String,
                                expectedSourceFolders: List<String> = emptyList(),
                                expectedAnnotationFolders: List<String> = emptyList(),
                                expectedGeneratedFolders: List<String> = emptyList(),
                                expectedExcludes: List<String> = emptyList()) {
    val actual = roots.find { it.contentRoot == expectedPath }

    if (actual == null) {
      throw AssertionError("Root $expectedPath not found among:\n\t" + (roots.map { it.contentRoot }).joinToString("\n\t"))
    }

    TestCase.assertEquals(expectedSourceFolders, actual.sourceFolders.map { it.path })
    TestCase.assertEquals(expectedAnnotationFolders, actual.annotationProcessorFolders.map { it.path })
    TestCase.assertEquals(expectedGeneratedFolders, actual.generatedFolders.map { it.path })
    TestCase.assertEquals(expectedExcludes, actual.excludedPaths)
  }
}