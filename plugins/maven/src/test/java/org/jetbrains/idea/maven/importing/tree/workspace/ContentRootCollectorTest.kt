// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.workspace

import com.intellij.maven.testFramework.MavenTestCase
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
      mapOf(
        sourceMain to JavaSourceRootType.SOURCE,
        resourceMain to JavaResourceRootType.RESOURCE,
        sourceTest to JavaSourceRootType.TEST_SOURCE,
      ),
      listOf(target),
      GeneratedFoldersHolder(annotationProcessorDirectory, annotationProcessorTestDirectory,
                             generatedSourceFolder, generatedTestSourceFolder)
    )

    Assert.assertEquals(1, contentRoots.size)
    val map = contentRootsHolder2Map(contentRoots)
    assertContentRoot(map, sourceMain, baseContentRoot)
    assertContentRoot(map, resourceMain, baseContentRoot)
    assertContentRoot(map, sourceTest, baseContentRoot)
    assertContentRoot(map, target, baseContentRoot)
    assertContentRoot(map, generatedSourceFolder, baseContentRoot)
    assertContentRoot(map, generatedTestSourceFolder, baseContentRoot)
    assertContentRoot(map, annotationProcessorDirectory, baseContentRoot)
    assertContentRoot(map, annotationProcessorTestDirectory, baseContentRoot)
  }

  @Test
  fun `test multiply content roots and empty exclude root and generated sources`() {
    val baseContentRoot = "/home/a/b/c/maven/src/main"
    val sourceMain = "/home/a/b/c/maven/src/main/java"
    val sourceMain2 = "/home/a/b/c/maven/java2"
    val target = "/home/a/b/c/maven/target"
    val generatedSourceFolder = "/home/a/b/c/maven/target/generated-sources/java"
    val generatedTestSourceFolder = "/home/a/b/c/maven/target/generated-sources-test/java"

    val contentRoots = ContentRootCollector.collect(
      baseContentRoot,
      mapOf(
        sourceMain to JavaSourceRootType.SOURCE,
        sourceMain2 to JavaSourceRootType.SOURCE
      ),
      listOf(target),
      GeneratedFoldersHolder(null, null, generatedSourceFolder, generatedTestSourceFolder)
    )

    Assert.assertEquals(4, contentRoots.size)
    val map = contentRootsHolder2Map(contentRoots)
    assertContentRoot(map, sourceMain, baseContentRoot)
    assertContentRoot(map, sourceMain2, sourceMain2)
    assertContentRoot(map, target, null)
    assertContentRoot(map, generatedSourceFolder, generatedSourceFolder)
    assertContentRoot(map, generatedTestSourceFolder, generatedTestSourceFolder)
  }

  @Test
  fun `test that target root created if needed`() {
    val baseContentRoot = "/home/a/b/c/maven"
    val target = "/home/a/b/c/maven/target"

    val contentRoots = ContentRootCollector.collect(
      baseContentRoot,
      emptyMap(),
      listOf(target),
      GeneratedFoldersHolder(null, null, null, null),
      true
    )

    Assert.assertEquals(1, contentRoots.size)
    val map = contentRootsHolder2Map(contentRoots)
    assertContentRoot(map, target, baseContentRoot)
  }

  @Test
  fun `test multiply source and resources content roots and annotation processor sources`() {
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
      GeneratedFoldersHolder(annotationProcessorDirectory, annotationProcessorTestDirectory, null, null)
    )

    Assert.assertEquals(9, contentRoots.size)
    val map = contentRootsHolder2Map(contentRoots)
    assertContentRoot(map, sourceMain, baseContentRoot)
    assertContentRoot(map, sourceMain2, sourceMain2)
    assertContentRoot(map, sourceMain3, sourceMain3)
    assertContentRoot(map, sourceMain4, sourceMain4)

    assertContentRoot(map, resourceMain, baseContentRoot)
    assertContentRoot(map, resourceMain2, resourceMain2)
    assertContentRoot(map, resourceMain3, resourceMain3)
    assertContentRoot(map, resourceMain4, resourceMain4)

    assertContentRoot(map, annotationProcessorDirectory, annotationProcessorDirectory)
    assertContentRoot(map, annotationProcessorTestDirectory, annotationProcessorTestDirectory)
  }

  private fun assertContentRoot(rootMap: Map<String, String>, path: String, root: String?) {
    Assert.assertEquals(root, rootMap.get(path))
  }

  private fun contentRootsHolder2Map(dataHolders: Collection<ContentRootDataHolder>): Map<String, String> {
    val contentRootByPath = mutableMapOf<String, String>()
    for (each in dataHolders) {
      for (sourceFolder in each.sourceFolders) {
        contentRootByPath.put(sourceFolder.path, each.contentRoot)
      }
      for (sourceFolder in each.generatedFolders) {
        contentRootByPath.put(sourceFolder.path, each.contentRoot)
      }
      for (sourceFolder in each.annotationProcessorFolders) {
        contentRootByPath.put(sourceFolder.path, each.contentRoot)
      }
      for (path in each.excludedPaths) {
        contentRootByPath.put(path, each.contentRoot)
      }
    }
    return contentRootByPath;
  }
}