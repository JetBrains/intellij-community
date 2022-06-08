// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree

import com.intellij.maven.testFramework.MavenTestCase
import junit.framework.TestCase
import org.jetbrains.idea.maven.importing.workspaceModel.ContentRootCollector
import org.jetbrains.idea.maven.importing.workspaceModel.ContentRootCollector.collect
import org.jetbrains.idea.maven.importing.workspaceModel.ContentRootDataHolder
import org.jetbrains.idea.maven.importing.workspaceModel.GeneratedFoldersHolder
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
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

    val contentRoots = collect(listOf(baseContentRoot), mapOf(
      sourceMain to JavaSourceRootType.SOURCE,
      resourceMain to JavaResourceRootType.RESOURCE,
      sourceTest to JavaSourceRootType.TEST_SOURCE,
    ),
                               listOf(target),
                               emptyList(),
                               GeneratedFoldersHolder(listOf(generatedSourceFolder),
                                                      listOf(generatedTestSourceFolder),
                                                      listOf(annotationProcessorDirectory),
                                                      listOf(annotationProcessorTestDirectory)))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(
                         expectedPath = baseContentRoot,
                         expectedSourceFolders = listOf(sourceMain, resourceMain, sourceTest),
                         expectedGeneratedFolders = listOf(annotationProcessorDirectory, annotationProcessorTestDirectory,
                                                           generatedSourceFolder, generatedTestSourceFolder),
                         expectedExcludes = listOf(target)))
    )
  }

  @Test
  fun `test do not register nested sources`() {
    val baseContentRoot = "/home"
    val source = "/home/source"
    val nestedSource = "/home/source/dir/nested"

    val contentRoots = collect(listOf(baseContentRoot), mapOf(source to JavaSourceRootType.SOURCE,
                                                              nestedSource to JavaSourceRootType.SOURCE),
                               listOf(),
                               emptyList(),
                               GeneratedFoldersHolder(listOf(), listOf()))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(expectedPath = baseContentRoot, expectedSourceFolders = listOf(source)))
    )
  }

  @Test
  fun `test do not register nested content root`() {
    val root1 = "/home"
    val source1 = "/home/source"
    val root2 = "/home/source/dir/nested"
    val source2 = "/home/source/dir/nested/source"

    val contentRoots = collect(listOf(root1, root2), mapOf(source1 to JavaSourceRootType.SOURCE, source2 to JavaSourceRootType.SOURCE),
                               listOf(), emptyList(), GeneratedFoldersHolder(listOf(), listOf()))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(expectedPath = root1, expectedSourceFolders = listOf(source1)))
    )
  }

  @Test
  fun `test do not register nested generated folder under a source folder`() {
    val root = "/project/"

    val source = "/project/source"
    val nestedGeneratedFolder = "/project/source/generated"
    val nestedOptionalGeneratedFolder = "/project/source/optional-generated"

    val contentRoots = collect(listOf(root), mapOf(source to JavaSourceRootType.SOURCE), listOf(), emptyList(),
                               GeneratedFoldersHolder(listOf(nestedGeneratedFolder), listOf(),
                                                      listOf(nestedOptionalGeneratedFolder), listOf()))
    assertContentRoots(contentRoots,
                       listOf(RootTestData(
                         expectedPath = root,
                         expectedSourceFolders = listOf(source),
                         expectedGeneratedFolders = listOf(),
                         expectedExcludes = listOf()))
    )
  }

  @Test
  fun `test do not register generated folder when there is a nested source or generated folder`() {
    val root = "/project/"

    val generatedWithNestedSourceFolder = "/project/generated-with-sources"
    val source = "/project/generated-with-sources/source"

    val generatedWithNestedGeneratedFolder = "/project/generated-with-generated"
    val generatedNestedFoldersHolder = "/project/generated-with-generated/generated"

    val generatedNoNestedFolders = "/project/target/generated-no-subsources/"

    val contentRoots = collect(listOf(root), mapOf(source to JavaSourceRootType.SOURCE), listOf(), emptyList(),
                               GeneratedFoldersHolder(listOf(generatedWithNestedSourceFolder,
                                                             generatedWithNestedGeneratedFolder,
                                                             generatedNestedFoldersHolder,
                                                             generatedNoNestedFolders),
                                                      listOf()))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(
                         expectedPath = root,
                         expectedSourceFolders = listOf(source),
                         expectedGeneratedFolders = listOf(generatedNestedFoldersHolder, generatedNoNestedFolders),
                         expectedExcludes = listOf()))
    )
  }

  @Test
  fun `test do not register generated folder with a nested source, but create a root`() {
    val root = "/root1/"

    val generatedWithNestedSourceFolder = "/generated-with-sources"
    val source = "/generated-with-sources/source"

    val generatedWithNestedGeneratedFolder = "/generated-with-generated"
    val generatedNestedFoldersHolder = "/generated-with-generated/generated"

    val contentRoots = collect(listOf(root), mapOf(source to JavaSourceRootType.SOURCE), listOf(), emptyList(),
                               GeneratedFoldersHolder(listOf(generatedWithNestedSourceFolder,
                                                             generatedWithNestedGeneratedFolder,
                                                             generatedNestedFoldersHolder),
                                                      listOf()))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(expectedPath = root,
                                           expectedSourceFolders = listOf(),
                                           expectedGeneratedFolders = listOf(),
                                           expectedExcludes = listOf()),
                              RootTestData(expectedPath = generatedWithNestedSourceFolder,
                                           expectedSourceFolders = listOf(source),
                                           expectedGeneratedFolders = listOf(),
                                           expectedExcludes = listOf()),
                              RootTestData(expectedPath = generatedWithNestedGeneratedFolder,
                                           expectedSourceFolders = listOf(),
                                           expectedGeneratedFolders = listOf(generatedNestedFoldersHolder),
                                           expectedExcludes = listOf()))
    )
  }

  @Test
  fun `test do not register optional generated folder when there is parent generated or source folder`() {
    val root = "/project/"

    val source = "/project/source"
    val generated = "/project/generated"
    val optionalGenerated = "/project/optional-generated"

    val optionalGeneratedUnderSource = "/project/source/optional-generated"
    val optionalGeneratedUnderGenerated = "/project/generated/optional-generated"
    val optionalGeneratedUnderOptionalGenerated = "/project/optional-generated/optional-generated"

    val contentRoots = collect(listOf(root), mapOf(source to JavaSourceRootType.SOURCE), listOf(), emptyList(),
                               GeneratedFoldersHolder(generatedSourceFolders = listOf(generated),
                                                      generatedTestSourceFolders = listOf(),
                                                      optionalGeneratedFolders = listOf(optionalGenerated,
                                                                                        optionalGeneratedUnderSource,
                                                                                        optionalGeneratedUnderGenerated,
                                                                                        optionalGeneratedUnderOptionalGenerated)))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(
                         expectedPath = root,
                         expectedSourceFolders = listOf(source),
                         expectedGeneratedFolders = listOf(generated, optionalGenerated),
                         expectedExcludes = listOf()))
    )
  }

  @Test
  fun `test folders outside of the content root`() {
    val baseContentRoot = "/home/content"
    val source = "/home/source"
    val generated = "/home/generated"
    val target = "/home/target" // will not be registered

    val contentRoots = collect(listOf(baseContentRoot), mapOf(source to JavaSourceRootType.SOURCE),
                               listOf(target),
                               emptyList(),
                               GeneratedFoldersHolder(listOf(generated), listOf()))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(expectedPath = baseContentRoot),
                              RootTestData(expectedPath = source, expectedSourceFolders = listOf(source)),
                              RootTestData(expectedPath = generated, expectedGeneratedFolders = listOf(generated)))
    )
  }

  @Test
  fun `test exclude folders`() {
    val contentRoots = collect(listOf("/home"),
                               mapOf("/home/src" to JavaSourceRootType.SOURCE,
                                     "/home/exclude1/src" to JavaSourceRootType.SOURCE,
                                     "/home/exclude6" to JavaSourceRootType.SOURCE),
                               listOf("/home/exclude1",
                                      "/home/exclude2",
                                      "/home/exclude3",
                                      "/home/exclude4",
                                      "/home/exclude5",
                                      "/home/exclude6",
                                      "/home/exclude7"),
                               emptyList(),
                               GeneratedFoldersHolder(listOf("/home/exclude2/annotations", "/home/exclude4/generated"),
                                                      listOf("/home/exclude3/annotations-test", "/home/exclude5/generated-test"))
    )

    assertContentRoots(contentRoots,
                       listOf(RootTestData(
                         expectedPath = "/home",
                         expectedSourceFolders = listOf("/home/exclude1/src", "/home/exclude6", "/home/src"),
                         expectedGeneratedFolders = listOf("/home/exclude2/annotations",
                                                           "/home/exclude3/annotations-test",
                                                           "/home/exclude4/generated",
                                                           "/home/exclude5/generated-test"),
                         expectedExcludes = listOf("/home/exclude1",
                                                   "/home/exclude2",
                                                   "/home/exclude3",
                                                   "/home/exclude4",
                                                   "/home/exclude5",
                                                   "/home/exclude6",
                                                   "/home/exclude7")))
    )
  }

  @Test
  fun `test do not register sole exclude folder`() {
    val contentRoots = collect(listOf(),
                               mapOf("/root/src" to JavaSourceRootType.SOURCE),
                               listOf("/root/exclude"),
                               emptyList(),
                               GeneratedFoldersHolder(listOf("/root/generated"), listOf())
    )
    assertContentRoots(contentRoots,
                       listOf(
                         RootTestData(
                           expectedPath = "/root/src",
                           expectedSourceFolders = listOf("/root/src"),
                           expectedGeneratedFolders = listOf(),
                           expectedExcludes = listOf()),
                         RootTestData(
                           expectedPath = "/root/generated",
                           expectedSourceFolders = listOf(),
                           expectedGeneratedFolders = listOf("/root/generated"),
                           expectedExcludes = listOf()))
    )
  }

  @Test
  fun `test do not register exclude folder pointing to a root`() {
    val contentRoots = collect(listOf("/root"),
                               mapOf("/root/src" to JavaSourceRootType.SOURCE),
                               listOf("/root"),
                               emptyList(),
                               GeneratedFoldersHolder(listOf("/root/generated"), listOf())
    )
    assertContentRoots(contentRoots,
                       listOf(
                         RootTestData(
                           expectedPath = "/root",
                           expectedSourceFolders = listOf("/root/src"),
                           expectedGeneratedFolders = listOf("/root/generated"),
                           expectedExcludes = listOf()))
    )
  }

  @Test
  fun `test exclude with do not register generated sources`() {
    val root = "/home"

    val excludeWithSource = "/home/exclude-with-source"
    val sourcesUnderExcluded = "/home/exclude-with-source/src"

    val excludeWithGenerated = "/home/exclude-with-generated"
    val generatedUnderExcluded = "/home/exclude-with-generated/generated"
    val excludeWithTestGenerated = "/home/exclude-with-test-generated"
    val testGeneratedUnderExcluded = "/home/exclude-with-test-generated/test-generated"

    val excludeWithOptionalGenerated = "/home/exclude-with-optional-generated"
    val optionalGeneratedUnderExcluded = "/home/exclude-with-optional-generated/optional-generated"
    val excludeWithTestOptionalGenerated = "/home/exclude-with-test-optional-generated"
    val testOptionalGeneratedUnderExcluded = "/home/exclude-with-test-optional-generated/test-optional-generated"

    val contentRoots = collect(listOf(root), mapOf(sourcesUnderExcluded to JavaSourceRootType.SOURCE),
                               emptyList(),
                               listOf(excludeWithSource,
                                      excludeWithGenerated,
                                      excludeWithTestGenerated,
                                      excludeWithOptionalGenerated,
                                      excludeWithTestOptionalGenerated),
                               GeneratedFoldersHolder(listOf(generatedUnderExcluded),
                                                      listOf(testGeneratedUnderExcluded),
                                                      listOf(optionalGeneratedUnderExcluded),
                                                      listOf(testOptionalGeneratedUnderExcluded)))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(
                         expectedPath = root,
                         expectedSourceFolders = listOf(sourcesUnderExcluded),
                         expectedGeneratedFolders = listOf(),
                         expectedExcludes = listOf(excludeWithSource,
                                                   excludeWithGenerated,
                                                   excludeWithTestGenerated,
                                                   excludeWithOptionalGenerated,
                                                   excludeWithTestOptionalGenerated)))
    )
  }


  @Test
  fun `test multiple content roots and empty exclude root and generated sources`() {
    val baseContentRoot = "/home/src/main"
    val sourceMain = "/home/src/main/java"
    val sourceMain2 = "/home/java2"
    val target = "/home/target"
    val generatedSourceFolder = "/home/target/generated-sources/java"
    val generatedTestSourceFolder = "/home/target/generated-sources-test/java"
    val optionalGeneratedSourceFolder = "/home/target/optional-generated-sources/java"
    val optionalGeneratedTestSourceFolder = "/home/target/optional-generated-sources-test/java"

    val contentRoots = collect(listOf(baseContentRoot),
                               mapOf(sourceMain to JavaSourceRootType.SOURCE,
                                     sourceMain2 to JavaSourceRootType.SOURCE),
                               listOf(target),
                               emptyList(),
                               GeneratedFoldersHolder(listOf(generatedSourceFolder), listOf(generatedTestSourceFolder),
                                                      listOf(optionalGeneratedSourceFolder), listOf(optionalGeneratedTestSourceFolder)))

    assertContentRoots(
      contentRoots,
      listOf(
        RootTestData(expectedPath = baseContentRoot, expectedSourceFolders = listOf(sourceMain)),
        RootTestData(expectedPath = sourceMain2, expectedSourceFolders = listOf(sourceMain2)),
        RootTestData(expectedPath = generatedSourceFolder, expectedGeneratedFolders = listOf(generatedSourceFolder)),
        RootTestData(expectedPath = generatedTestSourceFolder, expectedGeneratedFolders = listOf(generatedTestSourceFolder)),
        RootTestData(expectedPath = optionalGeneratedSourceFolder, expectedGeneratedFolders = listOf(optionalGeneratedSourceFolder)),
        RootTestData(expectedPath = optionalGeneratedTestSourceFolder,
                     expectedGeneratedFolders = listOf(optionalGeneratedTestSourceFolder)))
    )
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

    val contentRoots = collect(listOf(baseContentRoot), mapOf(
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
                               GeneratedFoldersHolder(listOf(annotationProcessorDirectory), listOf(annotationProcessorTestDirectory)))

    assertContentRoots(contentRoots, listOf(
      RootTestData(expectedPath = baseContentRoot, expectedSourceFolders = listOf(sourceMain, resourceMain)),
      RootTestData(expectedPath = sourceMain2, expectedSourceFolders = listOf(sourceMain2)),
      RootTestData(expectedPath = sourceMain3, expectedSourceFolders = listOf(sourceMain3)),
      RootTestData(expectedPath = sourceMain4, expectedSourceFolders = listOf(sourceMain4)),
      RootTestData(expectedPath = resourceMain2, expectedSourceFolders = listOf(resourceMain2)),
      RootTestData(expectedPath = resourceMain3, expectedSourceFolders = listOf(resourceMain3)),
      RootTestData(expectedPath = resourceMain4, expectedSourceFolders = listOf(resourceMain4)),
      RootTestData(expectedPath = annotationProcessorDirectory, expectedGeneratedFolders = listOf(annotationProcessorDirectory)),
      RootTestData(expectedPath = annotationProcessorTestDirectory, expectedGeneratedFolders = listOf(annotationProcessorTestDirectory)))
    )
  }

  private fun assertContentRoots(actualRoots: Collection<ContentRootDataHolder>,
                                 expectedRoots: Collection<RootTestData>) {

    val actualSorted = actualRoots.map {
      RootTestData(
        it.path,
        expectedSourceFolders = it.folders.filterIsInstance<ContentRootCollector.SourceFolderInfo>().map { it.path }.sorted(),
        expectedGeneratedFolders = it.folders.filterIsInstance<ContentRootCollector.BaseGeneratedSourceFolderInfo>().map { it.path }.sorted(),
        expectedExcludes = it.folders.filterIsInstance<ContentRootCollector.BaseExcludedFolderInfo>().map { it.path }.sorted(),
      )
    }.sortedBy { it.expectedPath }

    val expectedSorted = expectedRoots.map {
      it.copy(
        expectedSourceFolders = it.expectedSourceFolders.sorted(),
        expectedGeneratedFolders = it.expectedGeneratedFolders.sorted(),
        expectedExcludes = it.expectedExcludes.sorted()
      )
    }.sortedBy { it.expectedPath }

    TestCase.assertEquals(expectedSorted, actualSorted)
  }

  private data class RootTestData(val expectedPath: String,
                                  val expectedSourceFolders: List<String> = emptyList(),
                                  val expectedGeneratedFolders: List<String> = emptyList(),
                                  val expectedExcludes: List<String> = emptyList()) {
    override fun toString(): String {
      val result = StringBuilder()
      result.appendLine("{")
      result.append("Root: ").appendLine(expectedPath)

      fun appendIfNotEmpty(list: List<String>, type: String) {
        if (list.isNotEmpty()) {
          list.joinTo(result, prefix = "  $type:\n    ", separator = "\n    ", postfix = "\n")
        }
      }
      appendIfNotEmpty(expectedSourceFolders, "Sources")
      appendIfNotEmpty(expectedGeneratedFolders, "Generated")
      appendIfNotEmpty(expectedExcludes, "Excludes")
      result.appendLine("}")
      return result.toString()
    }
  }
}