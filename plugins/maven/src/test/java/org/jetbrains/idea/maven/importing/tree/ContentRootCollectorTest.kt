// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree

import com.intellij.maven.testFramework.MavenTestCase
import junit.framework.TestCase
import org.jetbrains.idea.maven.importing.workspaceModel.ContentRootCollector
import org.jetbrains.idea.maven.importing.workspaceModel.ContentRootCollector.collect
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
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

    val contentRoots = collect(projectRoots = listOf(baseContentRoot),
                               mainSourceFolders = listOf(sourceMain),
                               mainResourceFolders = listOf(resourceMain),
                               testSourceFolders = listOf(sourceTest),
                               mainGeneratedSourceFolders = listOf(generatedSourceFolder),
                               testGeneratedSourceFolders = listOf(generatedTestSourceFolder),
                               excludeFolders = listOf(target))

    assertContentRoots(contentRoots,
                       listOf(ContentRootTestData(
                         expectedPath = baseContentRoot,
                         expectedMainSourceFolders = listOf(sourceMain),
                         expectedMainResourcesFolders = listOf(resourceMain),
                         expectedTestSourceFolders = listOf(sourceTest),
                         expectedMainGeneratedFolders = listOf(generatedSourceFolder),
                         expectedTestGeneratedFolders = listOf(generatedTestSourceFolder),
                         expectedExcludes = listOf(target)))
    )
  }

  @Test
  fun `test do not register nested sources`() {
    val baseContentRoot = "/home"
    val source = "/home/source"
    val nestedSource = "/home/source/dir/nested"

    val contentRoots = collect(projectRoots = listOf(baseContentRoot),
                               mainSourceFolders = listOf(source, nestedSource))

    assertContentRoots(contentRoots,
                       listOf(ContentRootTestData(expectedPath = baseContentRoot,
                                                  expectedMainSourceFolders = listOf(source)))
    )
  }

  @Test
  fun `test normalizing relative paths`() {
    val contentRoots = collect(projectRoots = listOf("/home", "/home/../home"),
                               mainSourceFolders = listOf("/home/dir/../source", "/home/../source"))

    assertContentRoots(contentRoots,
                       listOf(ContentRootTestData(expectedPath = "/home",
                                                  expectedMainSourceFolders = listOf("/home/source")),
                              ContentRootTestData(expectedPath = "/source",
                                                  expectedMainSourceFolders = listOf("/source")))
    )
  }

  @Test
  fun `test source folders override resource folders`() {
    val root = "/home"
    val sourceMain = "/home/main/source"
    val sourceTest = "/home/test/source"

    val contentRoots = collect(projectRoots = listOf(root),
                               mainSourceFolders = listOf(sourceMain),
                               mainResourceFolders = listOf(sourceMain),
                               testSourceFolders = listOf(sourceTest),
                               testResourceFolders = listOf(sourceTest))

    assertContentRoots(contentRoots,
                       listOf(ContentRootTestData(
                         expectedPath = root,
                         expectedMainSourceFolders = listOf(sourceMain),
                         expectedMainResourcesFolders = listOf(),
                         expectedTestSourceFolders = listOf(sourceTest),
                         expectedTestResourcesFolders = listOf()))
    )
  }

  @Test
  fun `test source folders override test folders`() {
    val root = "/home"
    val sourceMain = "/home/main/source"
    val resourceMain = "/home/main/resource"
    val generatedSourceFolder = "/home/main/generated-sources"

    val contentRoots = collect(projectRoots = listOf(root),
                               mainSourceFolders = listOf(sourceMain),
                               mainResourceFolders = listOf(resourceMain),
                               testSourceFolders = listOf(sourceMain),
                               testResourceFolders = listOf(resourceMain),
                               mainGeneratedSourceFolders = listOf(generatedSourceFolder),
                               testGeneratedSourceFolders = listOf(generatedSourceFolder))

    assertContentRoots(contentRoots,
                       listOf(ContentRootTestData(
                         expectedPath = root,
                         expectedMainSourceFolders = listOf(sourceMain),
                         expectedMainResourcesFolders = listOf(resourceMain),
                         expectedTestSourceFolders = listOf(),
                         expectedTestResourcesFolders = listOf(),
                         expectedMainGeneratedFolders = listOf(generatedSourceFolder),
                         expectedTestGeneratedFolders = listOf()))
    )
  }

  @Test
  fun `test do not register nested content root`() {
    val root1 = "/home"
    val source1 = "/home/source"
    val root2 = "/home/source/dir/nested"
    val source2 = "/home/source/dir/nested/source"

    val contentRoots = collect(projectRoots = listOf(root1, root2),
                               mainSourceFolders = listOf(source1, source2))

    assertContentRoots(contentRoots,
                       listOf(ContentRootTestData(expectedPath = root1,
                                                  expectedMainSourceFolders = listOf(source1)))
    )
  }

  @Test
  fun `test do not register nested generated folder under a source folder`() {
    val root = "/project"

    val source = "/project/source"
    val nestedGeneratedFolder = "/project/source/generated"

    val contentRoots = collect(projectRoots = listOf(root),
                               mainSourceFolders = listOf(source),
                               mainGeneratedSourceFolders = listOf(nestedGeneratedFolder))
    assertContentRoots(contentRoots,
                       listOf(ContentRootTestData(
                         expectedPath = root,
                         expectedMainSourceFolders = listOf(source),
                         expectedMainGeneratedFolders = listOf()))
    )
  }

  @Test
  fun `test do not register generated folder when there is a nested source or generated folder`() {
    val root = "/project"

    val generatedWithNestedSourceFolder = "/project/generated-with-sources"
    val source = "/project/generated-with-sources/source"

    val generatedWithNestedGeneratedFolder = "/project/generated-with-generated"
    val generatedNestedFoldersHolder = "/project/generated-with-generated/generated"

    val generatedNoNestedFolders = "/project/target/generated-no-subsources"

    val contentRoots = collect(projectRoots = listOf(root),
                               mainSourceFolders = listOf(source),
                               mainGeneratedSourceFolders = listOf(generatedWithNestedSourceFolder,
                                                                   generatedWithNestedGeneratedFolder,
                                                                   generatedNestedFoldersHolder,
                                                                   generatedNoNestedFolders))

    assertContentRoots(contentRoots,
                       listOf(ContentRootTestData(
                         expectedPath = root,
                         expectedMainSourceFolders = listOf(source),
                         expectedMainGeneratedFolders = listOf(generatedNestedFoldersHolder,
                                                               generatedNoNestedFolders)))
    )
  }

  @Test
  fun `test do not register generated folder with a nested source, but create a root`() {
    val root = "/root1"

    val generatedWithNestedSourceFolder = "/generated-with-sources"
    val source = "/generated-with-sources/source"

    val generatedWithNestedGeneratedFolder = "/generated-with-generated"
    val generatedNestedFoldersHolder = "/generated-with-generated/generated"

    val contentRoots = collect(
      projectRoots = listOf(root),
      mainSourceFolders = listOf(source),
      mainGeneratedSourceFolders = listOf(generatedWithNestedSourceFolder,
                                          generatedWithNestedGeneratedFolder,
                                          generatedNestedFoldersHolder),
    )

    assertContentRoots(contentRoots,
                       listOf(ContentRootTestData(expectedPath = root,
                                                  expectedMainSourceFolders = listOf(),
                                                  expectedMainGeneratedFolders = listOf()),
                              ContentRootTestData(expectedPath = generatedWithNestedSourceFolder,
                                                  expectedMainSourceFolders = listOf(source),
                                                  expectedMainGeneratedFolders = listOf()),
                              ContentRootTestData(expectedPath = generatedWithNestedGeneratedFolder,
                                                  expectedMainSourceFolders = listOf(),
                                                  expectedMainGeneratedFolders = listOf(generatedNestedFoldersHolder)))
    )
  }

  @Test
  fun `test folders outside of the content root`() {
    val baseContentRoot = "/home/content"
    val source = "/home/source"
    val generated = "/home/generated"
    val target = "/home/target" // will not be registered

    val contentRoots = collect(projectRoots = listOf(baseContentRoot),
                               mainSourceFolders = listOf(source),
                               mainGeneratedSourceFolders = listOf(generated),
                               excludeFolders = listOf(target))

    assertContentRoots(contentRoots,
                       listOf(ContentRootTestData(expectedPath = baseContentRoot),
                              ContentRootTestData(expectedPath = source, expectedMainSourceFolders = listOf(source)),
                              ContentRootTestData(expectedPath = generated, expectedMainGeneratedFolders = listOf(generated)))
    )
  }

  @Test
  fun `test exclude folders`() {
    val contentRoots = collect(projectRoots = listOf("/home"),
                               mainSourceFolders = listOf("/home/src",
                                                          "/home/exclude1/src",
                                                          "/home/exclude6"),
                               mainGeneratedSourceFolders = listOf("/home/exclude4/generated"),
                               testGeneratedSourceFolders = listOf("/home/exclude5/generated-test"),
                               excludeFolders = listOf("/home/exclude1",
                                                       "/home/exclude2",
                                                       "/home/exclude3",
                                                       "/home/exclude4",
                                                       "/home/exclude5",
                                                       "/home/exclude6",
                                                       "/home/exclude7")
    )

    assertContentRoots(contentRoots,
                       listOf(ContentRootTestData(
                         expectedPath = "/home",
                         expectedMainSourceFolders = listOf("/home/src",
                                                            "/home/exclude1/src",
                                                            "/home/exclude6"),
                         expectedMainGeneratedFolders = listOf("/home/exclude4/generated"),
                         expectedTestGeneratedFolders = listOf("/home/exclude5/generated-test"),
                         expectedExcludes = listOf("/home/exclude1",
                                                   "/home/exclude2",
                                                   "/home/exclude3",
                                                   "/home/exclude4",
                                                   "/home/exclude5",
                                                   "/home/exclude7")))
    )
  }

  @Test
  fun `test do not register content root for a single exclude folder`() {
    val contentRoots = collect(projectRoots = listOf(),
                               mainSourceFolders = listOf("/root/src"),
                               mainGeneratedSourceFolders = listOf("/root/generated"),
                               excludeFolders = listOf("/root/exclude"))
    assertContentRoots(contentRoots,
                       listOf(
                         ContentRootTestData(
                           expectedPath = "/root/src",
                           expectedMainSourceFolders = listOf("/root/src"),
                           expectedMainGeneratedFolders = listOf(),
                           expectedExcludes = listOf()),
                         ContentRootTestData(
                           expectedPath = "/root/generated",
                           expectedMainSourceFolders = listOf(),
                           expectedMainGeneratedFolders = listOf("/root/generated"),
                           expectedExcludes = listOf()))
    )
  }

  @Test
  fun `test do not register nested exclude folder`() {
    val root = "/root"
    val exclude = "/root/exclude"
    val nestedExclude = "/root/exclude/exclude"
    val nestedExcludeAndPreventSubfolders = "/root/exclude/exclude-no-subfolders"

    val excludeAndPreventSubfolders = "/root/exclude-no-subfolders"
    val excludeAndPreventSubfolders_NestedExclude = "/root/exclude-no-subfolders/exclude"
    val excludeAndPreventSubfolders_NestedExcludeAndPreventSubfolders = "/root/exclude-no-subfolders/exclude-no-subfolders"


    val contentRoots = collect(projectRoots = listOf(root),
                               excludeFolders = listOf(exclude,
                                                       nestedExclude,
                                                       excludeAndPreventSubfolders_NestedExclude),
                               excludeAndPreventSubfoldersFolders = listOf(nestedExcludeAndPreventSubfolders,
                                                                           excludeAndPreventSubfolders,
                                                                           excludeAndPreventSubfolders_NestedExcludeAndPreventSubfolders))
    assertContentRoots(contentRoots,
                       listOf(ContentRootTestData(
                         expectedPath = root,
                         expectedExcludes = listOf(exclude, excludeAndPreventSubfolders)))
    )
  }

  @Test
  fun `test do not register exclude folder pointing to a root`() {
    val contentRoots = collect(projectRoots = listOf("/root"),
                               mainSourceFolders = listOf("/root/src"),
                               mainGeneratedSourceFolders = listOf("/root/generated"),
                               excludeFolders = listOf("/root"))

    assertContentRoots(contentRoots,
                       listOf(
                         ContentRootTestData(
                           expectedPath = "/root",
                           expectedMainSourceFolders = listOf("/root/src"),
                           expectedMainGeneratedFolders = listOf("/root/generated"),
                           expectedExcludes = listOf()))
    )
  }

  @Test
  fun `test exclude and prevent nested folders`() {
    val root = "/home"

    val excludeWithSource = "/home/exclude-with-source"
    val sourcesUnderExcluded = "/home/exclude-with-source/src"

    val excludeWithGenerated = "/home/exclude-with-generated"
    val generatedUnderExcluded = "/home/exclude-with-generated/generated"
    val excludeWithTestGenerated = "/home/exclude-with-test-generated"
    val testGeneratedUnderExcluded = "/home/exclude-with-test-generated/test-generated"

    val contentRoots = collect(projectRoots = listOf(root),
                               mainSourceFolders = listOf(sourcesUnderExcluded),
                               mainGeneratedSourceFolders = listOf(generatedUnderExcluded),
                               testGeneratedSourceFolders = listOf(testGeneratedUnderExcluded),
                               excludeAndPreventSubfoldersFolders = listOf(excludeWithSource,
                                                                           excludeWithGenerated,
                                                                           excludeWithTestGenerated))

    assertContentRoots(contentRoots,
                       listOf(ContentRootTestData(
                         expectedPath = root,
                         expectedMainSourceFolders = listOf(),
                         expectedTestSourceFolders = listOf(),
                         expectedMainGeneratedFolders = listOf(),
                         expectedTestGeneratedFolders = listOf(),
                         expectedExcludes = listOf(excludeWithSource,
                                                   excludeWithGenerated,
                                                   excludeWithTestGenerated)))
    )
  }


  @Test
  fun `test multiple content roots and empty exclude root and generated sources`() {
    val baseContentRoot = "/home/src/main"
    val sourceMain = "/home/src/main/java"
    val sourceMain2 = "/home/java2"
    val target = "/home/target"
    val generatedSourceFolder = "/home/target/generated-sources/java"
    val testGeneratedSourceFolder = "/home/target/test-generated-sources/java"

    val contentRoots = collect(projectRoots = listOf(baseContentRoot),
                               mainSourceFolders = listOf(sourceMain, sourceMain2),
                               mainGeneratedSourceFolders = listOf(generatedSourceFolder),
                               testGeneratedSourceFolders = listOf(testGeneratedSourceFolder),
                               excludeFolders = listOf(target),
                               excludeAndPreventSubfoldersFolders = emptyList())

    assertContentRoots(
      contentRoots,
      listOf(
        ContentRootTestData(expectedPath = baseContentRoot, expectedMainSourceFolders = listOf(sourceMain)),
        ContentRootTestData(expectedPath = sourceMain2, expectedMainSourceFolders = listOf(sourceMain2)),
        ContentRootTestData(expectedPath = generatedSourceFolder, expectedMainGeneratedFolders = listOf(generatedSourceFolder)),
        ContentRootTestData(expectedPath = testGeneratedSourceFolder, expectedTestGeneratedFolders = listOf(testGeneratedSourceFolder)))
    )
  }

  @Test
  fun `test multiple source and resources content roots and generated sources`() {
    val baseContentRoot = "/home/a/b/c/maven/src/main"
    val sourceMain = "/home/a/b/c/maven/src/main/java"
    val sourceMain2 = "/home/a/b/c/maven/java2"
    val sourceMain3 = "/home/a/b/c/maven/main/java3"
    val sourceMain4 = "/home/a/b/c/other/java4"

    val resourceMain = "/home/a/b/c/maven/src/main/resource"
    val resourceMain2 = "/home/a/b/c/maven/resource2"
    val resourceMain3 = "/home/a/b/c/maven/main/resource3"
    val resourceMain4 = "/home/a/b/c/other/resource4"

    val generatedDirectory = "/home/a/b/c/maven/target/generated/java"
    val generatedTestDirectory = "/home/a/b/c/maven/target/generated-test/java"

    val contentRoots = collect(projectRoots = listOf(baseContentRoot),
                               mainSourceFolders = listOf(
                                 sourceMain4,
                                 sourceMain3,
                                 sourceMain2,
                                 sourceMain),
                               mainResourceFolders = listOf(
                                 resourceMain4,
                                 resourceMain3,
                                 resourceMain2,
                                 resourceMain,
                               ),
                               mainGeneratedSourceFolders = listOf(generatedDirectory),
                               testGeneratedSourceFolders = listOf(generatedTestDirectory))

    assertContentRoots(contentRoots, listOf(
      ContentRootTestData(expectedPath = baseContentRoot,
                          expectedMainSourceFolders = listOf(sourceMain),
                          expectedMainResourcesFolders = listOf(resourceMain)),
      ContentRootTestData(expectedPath = sourceMain2, expectedMainSourceFolders = listOf(sourceMain2)),
      ContentRootTestData(expectedPath = sourceMain3, expectedMainSourceFolders = listOf(sourceMain3)),
      ContentRootTestData(expectedPath = sourceMain4, expectedMainSourceFolders = listOf(sourceMain4)),
      ContentRootTestData(expectedPath = resourceMain2, expectedMainResourcesFolders = listOf(resourceMain2)),
      ContentRootTestData(expectedPath = resourceMain3, expectedMainResourcesFolders = listOf(resourceMain3)),
      ContentRootTestData(expectedPath = resourceMain4, expectedMainResourcesFolders = listOf(resourceMain4)),
      ContentRootTestData(expectedPath = generatedDirectory, expectedMainGeneratedFolders = listOf(generatedDirectory)),
      ContentRootTestData(expectedPath = generatedTestDirectory, expectedTestGeneratedFolders = listOf(generatedTestDirectory)))
    )
  }

  private fun collect(projectRoots: List<String> = emptyList(),
                      mainSourceFolders: List<String> = emptyList(),
                      mainResourceFolders: List<String> = emptyList(),
                      testSourceFolders: List<String> = emptyList(),
                      testResourceFolders: List<String> = emptyList(),
                      mainGeneratedSourceFolders: List<String> = emptyList(),
                      testGeneratedSourceFolders: List<String> = emptyList(),
                      excludeFolders: List<String> = emptyList(),
                      excludeAndPreventSubfoldersFolders: List<String> = emptyList()): Collection<ContentRootCollector.ContentRootResult> {
    val folders = mutableListOf<ContentRootCollector.ImportedFolder>()

    projectRoots.forEach { folders.add(ContentRootCollector.ProjectRootFolder(it)) }

    mainSourceFolders.forEach { folders.add(ContentRootCollector.SourceFolder(it, JavaSourceRootType.SOURCE)) }
    mainResourceFolders.forEach { folders.add(ContentRootCollector.SourceFolder(it, JavaResourceRootType.RESOURCE)) }
    mainGeneratedSourceFolders.forEach {
      folders.add(ContentRootCollector.GeneratedSourceFolder(it, JavaSourceRootType.SOURCE))
    }

    testSourceFolders.forEach { folders.add(ContentRootCollector.SourceFolder(it, JavaSourceRootType.TEST_SOURCE)) }
    testResourceFolders.forEach { folders.add(ContentRootCollector.SourceFolder(it, JavaResourceRootType.TEST_RESOURCE)) }
    testGeneratedSourceFolders.forEach {
      folders.add(ContentRootCollector.GeneratedSourceFolder(it, JavaSourceRootType.TEST_SOURCE))
    }

    excludeFolders.forEach { folders.add(ContentRootCollector.ExcludedFolder(it)) }
    excludeAndPreventSubfoldersFolders.forEach { folders.add(ContentRootCollector.ExcludedFolderAndPreventSubfolders(it)) }

    return collect(folders)
  }

  private fun assertContentRoots(actualRoots: Collection<ContentRootCollector.ContentRootResult>,
                                 expectedRoots: Collection<ContentRootTestData>) {
    fun mapPaths(result: ContentRootCollector.ContentRootResult,
                 type: JpsModuleSourceRootType<*>,
                 isGenerated: Boolean) =
      result.sourceFolders.filter { it.type == type && it.isGenerated == isGenerated }.map { it.path }.sorted()

    val actualSorted = actualRoots.map {
      ContentRootTestData(
        it.path,
        expectedMainSourceFolders = mapPaths(it, JavaSourceRootType.SOURCE, isGenerated = false),
        expectedMainResourcesFolders = mapPaths(it, JavaResourceRootType.RESOURCE, isGenerated = false),
        expectedMainGeneratedFolders = mapPaths(it, JavaSourceRootType.SOURCE, isGenerated = true),

        expectedTestSourceFolders = mapPaths(it, JavaSourceRootType.TEST_SOURCE, isGenerated = false),
        expectedTestResourcesFolders = mapPaths(it, JavaResourceRootType.TEST_RESOURCE, isGenerated = false),
        expectedTestGeneratedFolders = mapPaths(it, JavaSourceRootType.TEST_SOURCE, isGenerated = true),

        expectedExcludes = it.excludeFolders.map { it.path }.sorted(),
      )
    }.sortedBy { it.expectedPath }

    val expectedSorted = expectedRoots.map {
      it.copy(
        expectedMainSourceFolders = it.expectedMainSourceFolders.sorted(),
        expectedMainResourcesFolders = it.expectedMainResourcesFolders.sorted(),
        expectedMainGeneratedFolders = it.expectedMainGeneratedFolders.sorted(),

        expectedTestSourceFolders = it.expectedTestSourceFolders.sorted(),
        expectedTestResourcesFolders = it.expectedTestResourcesFolders.sorted(),
        expectedTestGeneratedFolders = it.expectedTestGeneratedFolders.sorted(),

        expectedExcludes = it.expectedExcludes.sorted()
      )
    }.sortedBy { it.expectedPath }

    TestCase.assertEquals(expectedSorted, actualSorted)
  }

  private data class ContentRootTestData(val expectedPath: String,
                                         val expectedMainSourceFolders: List<String> = emptyList(),
                                         val expectedTestSourceFolders: List<String> = emptyList(),
                                         val expectedMainResourcesFolders: List<String> = emptyList(),
                                         val expectedTestResourcesFolders: List<String> = emptyList(),
                                         val expectedMainGeneratedFolders: List<String> = emptyList(),
                                         val expectedTestGeneratedFolders: List<String> = emptyList(),
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
      appendIfNotEmpty(expectedMainSourceFolders, "Main Sources")
      appendIfNotEmpty(expectedMainResourcesFolders, "Main Resources")
      appendIfNotEmpty(expectedMainGeneratedFolders, "Main Generated")
      appendIfNotEmpty(expectedTestSourceFolders, "Test Sources")
      appendIfNotEmpty(expectedTestResourcesFolders, "Test Resources")
      appendIfNotEmpty(expectedTestGeneratedFolders, "Test Generated")
      appendIfNotEmpty(expectedExcludes, "Excludes")
      result.appendLine("}")
      return result.toString()
    }
  }
}