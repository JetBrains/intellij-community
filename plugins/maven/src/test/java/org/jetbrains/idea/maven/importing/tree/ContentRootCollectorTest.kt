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
    val annotationProcessorDirectory = "/home/a/b/c/maven/target/annotation-processor/java"
    val annotationProcessorTestDirectory = "/home/a/b/c/maven/target/annotation-processor-test/java"

    val contentRoots = collect(contentRoots = listOf(baseContentRoot),
                               mainSourceFolders = listOf(sourceMain),
                               mainResourceFolders = listOf(resourceMain),
                               testSourceFolders = listOf(sourceTest),
                               mainGeneratedSourceFolders = listOf(generatedSourceFolder),
                               mainOptionalGeneratedFolders = listOf(annotationProcessorDirectory),
                               testGeneratedSourceFolders = listOf(generatedTestSourceFolder),
                               testOptionalGeneratedFolders = listOf(annotationProcessorTestDirectory),
                               excludeFolders = listOf(target))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(
                         expectedPath = baseContentRoot,
                         expectedMainSourceFolders = listOf(sourceMain),
                         expectedMainResourcesFolders = listOf(resourceMain),
                         expectedTestSourceFolders = listOf(sourceTest),
                         expectedMainGeneratedFolders = listOf(generatedSourceFolder,
                                                               annotationProcessorDirectory),
                         expectedTestGeneratedFolders = listOf(generatedTestSourceFolder,
                                                               annotationProcessorTestDirectory),
                         expectedExcludes = listOf(target)))
    )
  }

  @Test
  fun `test do not register nested sources`() {
    val baseContentRoot = "/home"
    val source = "/home/source"
    val nestedSource = "/home/source/dir/nested"

    val contentRoots = collect(contentRoots = listOf(baseContentRoot),
                               mainSourceFolders = listOf(source, nestedSource))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(expectedPath = baseContentRoot,
                                           expectedMainSourceFolders = listOf(source)))
    )
  }

  @Test
  fun `test source folders override resource folders`() {
    val root = "/home"
    val sourceMain = "/home/main/source"
    val sourceTest = "/home/test/source"

    val contentRoots = collect(contentRoots = listOf(root),
                               mainSourceFolders = listOf(sourceMain),
                               mainResourceFolders = listOf(sourceMain),
                               testSourceFolders = listOf(sourceTest),
                               testResourceFolders = listOf(sourceTest))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(
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
    val annotationProcessorDirectory = "/home/main/annotation-sources"

    val contentRoots = collect(contentRoots = listOf(root),
                               mainSourceFolders = listOf(sourceMain),
                               mainResourceFolders = listOf(resourceMain),
                               testSourceFolders = listOf(sourceMain),
                               testResourceFolders = listOf(resourceMain),
                               mainGeneratedSourceFolders = listOf(generatedSourceFolder),
                               mainOptionalGeneratedFolders = listOf(annotationProcessorDirectory),
                               testGeneratedSourceFolders = listOf(generatedSourceFolder),
                               testOptionalGeneratedFolders = listOf(annotationProcessorDirectory))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(
                         expectedPath = root,
                         expectedMainSourceFolders = listOf(sourceMain),
                         expectedMainResourcesFolders = listOf(resourceMain),
                         expectedTestSourceFolders = listOf(),
                         expectedTestResourcesFolders = listOf(),
                         expectedMainGeneratedFolders = listOf(generatedSourceFolder,
                                                               annotationProcessorDirectory),
                         expectedTestGeneratedFolders = listOf()))
    )
  }

  @Test
  fun `test do not register nested content root`() {
    val root1 = "/home"
    val source1 = "/home/source"
    val root2 = "/home/source/dir/nested"
    val source2 = "/home/source/dir/nested/source"

    val contentRoots = collect(contentRoots = listOf(root1, root2),
                               mainSourceFolders = listOf(source1, source2))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(expectedPath = root1,
                                           expectedMainSourceFolders = listOf(source1)))
    )
  }

  @Test
  fun `test do not register nested generated folder under a source folder`() {
    val root = "/project/"

    val source = "/project/source"
    val nestedGeneratedFolder = "/project/source/generated"
    val nestedOptionalGeneratedFolder = "/project/source/optional-generated"

    val contentRoots = collect(contentRoots = listOf(root),
                               mainSourceFolders = listOf(source),
                               mainGeneratedSourceFolders = listOf(nestedGeneratedFolder),
                               mainOptionalGeneratedFolders = listOf(nestedOptionalGeneratedFolder))
    assertContentRoots(contentRoots,
                       listOf(RootTestData(
                         expectedPath = root,
                         expectedMainSourceFolders = listOf(source),
                         expectedMainGeneratedFolders = listOf()))
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

    val contentRoots = collect(contentRoots = listOf(root),
                               mainSourceFolders = listOf(source),
                               mainGeneratedSourceFolders = listOf(generatedWithNestedSourceFolder,
                                                                   generatedWithNestedGeneratedFolder,
                                                                   generatedNestedFoldersHolder,
                                                                   generatedNoNestedFolders))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(
                         expectedPath = root,
                         expectedMainSourceFolders = listOf(source),
                         expectedMainGeneratedFolders = listOf(generatedNestedFoldersHolder,
                                                               generatedNoNestedFolders)))
    )
  }

  @Test
  fun `test do not register generated folder with a nested source, but create a root`() {
    val root = "/root1/"

    val generatedWithNestedSourceFolder = "/generated-with-sources"
    val source = "/generated-with-sources/source"

    val generatedWithNestedGeneratedFolder = "/generated-with-generated"
    val generatedNestedFoldersHolder = "/generated-with-generated/generated"

    val contentRoots = collect(
      contentRoots = listOf(root),
      mainSourceFolders = listOf(source),
      mainGeneratedSourceFolders = listOf(generatedWithNestedSourceFolder,
                                          generatedWithNestedGeneratedFolder,
                                          generatedNestedFoldersHolder),
    )

    assertContentRoots(contentRoots,
                       listOf(RootTestData(expectedPath = root,
                                           expectedMainSourceFolders = listOf(),
                                           expectedMainGeneratedFolders = listOf()),
                              RootTestData(expectedPath = generatedWithNestedSourceFolder,
                                           expectedMainSourceFolders = listOf(source),
                                           expectedMainGeneratedFolders = listOf()),
                              RootTestData(expectedPath = generatedWithNestedGeneratedFolder,
                                           expectedMainSourceFolders = listOf(),
                                           expectedMainGeneratedFolders = listOf(generatedNestedFoldersHolder)))
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

    val contentRoots = collect(listOf(root),
                               listOf(source),
                               mainGeneratedSourceFolders = listOf(generated),
                               testGeneratedSourceFolders = listOf(),
                               mainOptionalGeneratedFolders = listOf(optionalGenerated,
                                                                     optionalGeneratedUnderSource,
                                                                     optionalGeneratedUnderGenerated,
                                                                     optionalGeneratedUnderOptionalGenerated))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(
                         expectedPath = root,
                         expectedMainSourceFolders = listOf(source),
                         expectedMainGeneratedFolders = listOf(generated, optionalGenerated)))
    )
  }

  @Test
  fun `test folders outside of the content root`() {
    val baseContentRoot = "/home/content"
    val source = "/home/source"
    val generated = "/home/generated"
    val target = "/home/target" // will not be registered

    val contentRoots = collect(contentRoots = listOf(baseContentRoot),
                               mainSourceFolders = listOf(source),
                               excludeFolders = listOf(target),
                               mainGeneratedSourceFolders = listOf(generated))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(expectedPath = baseContentRoot),
                              RootTestData(expectedPath = source, expectedMainSourceFolders = listOf(source)),
                              RootTestData(expectedPath = generated, expectedMainGeneratedFolders = listOf(generated)))
    )
  }

  @Test
  fun `test exclude folders`() {
    val contentRoots = collect(contentRoots = listOf("/home"),
                               mainSourceFolders = listOf("/home/src",
                                                          "/home/exclude1/src",
                                                          "/home/exclude6"),
                               excludeFolders = listOf("/home/exclude1",
                                                       "/home/exclude2",
                                                       "/home/exclude3",
                                                       "/home/exclude4",
                                                       "/home/exclude5",
                                                       "/home/exclude6",
                                                       "/home/exclude7"),
                               mainGeneratedSourceFolders = listOf("/home/exclude2/annotations", "/home/exclude4/generated"),
                               testGeneratedSourceFolders = listOf("/home/exclude3/annotations-test", "/home/exclude5/generated-test")
    )

    assertContentRoots(contentRoots,
                       listOf(RootTestData(
                         expectedPath = "/home",
                         expectedMainSourceFolders = listOf("/home/src",
                                                            "/home/exclude1/src",
                                                            "/home/exclude6"),
                         expectedMainGeneratedFolders = listOf("/home/exclude2/annotations",
                                                               "/home/exclude4/generated"),
                         expectedTestGeneratedFolders = listOf("/home/exclude3/annotations-test",
                                                               "/home/exclude5/generated-test"),
                         expectedExcludes = listOf("/home/exclude1",
                                                   "/home/exclude2",
                                                   "/home/exclude3",
                                                   "/home/exclude4",
                                                   "/home/exclude5",
                                                   "/home/exclude7")))
    )
  }

  @Test
  fun `test do not register sole exclude folder`() {
    val contentRoots = collect(contentRoots = listOf(),
                               mainSourceFolders = listOf("/root/src"),
                               excludeFolders = listOf("/root/exclude"),
                               mainGeneratedSourceFolders = listOf("/root/generated"))
    assertContentRoots(contentRoots,
                       listOf(
                         RootTestData(
                           expectedPath = "/root/src",
                           expectedMainSourceFolders = listOf("/root/src"),
                           expectedMainGeneratedFolders = listOf(),
                           expectedExcludes = listOf()),
                         RootTestData(
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
    val nestedExcludeNoGenerated = "/root/exclude/exclude-no-generated"

    val excludeNoGenerated = "/root/exclude-no-generated"
    val excludeNoGeneratedNestedExclude = "/root/exclude-no-generated/exclude"
    val excludeNoGeneratedNestedNoGenerated = "/root/exclude-no-generated/exclude-no-generated"


    val contentRoots = collect(contentRoots = listOf(root),
                               excludeFolders = listOf(exclude,
                                                       nestedExclude,
                                                       excludeNoGeneratedNestedExclude),
                               excludeNoSubSourceFolders = listOf(nestedExcludeNoGenerated,
                                                                  excludeNoGenerated,
                                                                  excludeNoGeneratedNestedNoGenerated))
    assertContentRoots(contentRoots,
                       listOf(RootTestData(
                         expectedPath = root,
                         expectedExcludes = listOf(exclude, excludeNoGenerated)))
    )
  }

  @Test
  fun `test do not register exclude folder pointing to a root`() {
    val contentRoots = collect(contentRoots = listOf("/root"),
                               mainSourceFolders = listOf("/root/src"),
                               excludeFolders = listOf("/root"),
                               mainGeneratedSourceFolders = listOf("/root/generated"))

    assertContentRoots(contentRoots,
                       listOf(
                         RootTestData(
                           expectedPath = "/root",
                           expectedMainSourceFolders = listOf("/root/src"),
                           expectedMainGeneratedFolders = listOf("/root/generated"),
                           expectedExcludes = listOf()))
    )
  }

  @Test
  fun `test exclude prevent nested source and generated folders`() {
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

    val contentRoots = collect(contentRoots = listOf(root),
                               mainSourceFolders = listOf(sourcesUnderExcluded),
                               excludeNoSubSourceFolders = listOf(excludeWithSource,
                                                                  excludeWithGenerated,
                                                                  excludeWithTestGenerated,
                                                                  excludeWithOptionalGenerated,
                                                                  excludeWithTestOptionalGenerated),
                               mainGeneratedSourceFolders = listOf(generatedUnderExcluded),
                               mainOptionalGeneratedFolders = listOf(optionalGeneratedUnderExcluded),
                               testGeneratedSourceFolders = listOf(testGeneratedUnderExcluded),
                               testOptionalGeneratedFolders = listOf(testOptionalGeneratedUnderExcluded))

    assertContentRoots(contentRoots,
                       listOf(RootTestData(
                         expectedPath = root,
                         expectedMainSourceFolders = listOf(),
                         expectedTestSourceFolders = listOf(),
                         expectedMainGeneratedFolders = listOf(),
                         expectedTestGeneratedFolders = listOf(),
                         expectedExcludes = listOf(excludeWithSource,
                                                   excludeWithGenerated,
                                                   excludeWithOptionalGenerated,
                                                   excludeWithTestGenerated,
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

    val contentRoots = collect(contentRoots = listOf(baseContentRoot),
                               mainSourceFolders = listOf(sourceMain, sourceMain2),
                               excludeFolders = listOf(target),
                               excludeNoSubSourceFolders = emptyList(),
                               mainGeneratedSourceFolders = listOf(generatedSourceFolder),
                               mainOptionalGeneratedFolders = listOf(optionalGeneratedSourceFolder),
                               testGeneratedSourceFolders = listOf(generatedTestSourceFolder),
                               testOptionalGeneratedFolders = listOf(optionalGeneratedTestSourceFolder))

    assertContentRoots(
      contentRoots,
      listOf(
        RootTestData(expectedPath = baseContentRoot, expectedMainSourceFolders = listOf(sourceMain)),
        RootTestData(expectedPath = sourceMain2, expectedMainSourceFolders = listOf(sourceMain2)),
        RootTestData(expectedPath = generatedSourceFolder, expectedMainGeneratedFolders = listOf(generatedSourceFolder)),
        RootTestData(expectedPath = generatedTestSourceFolder, expectedTestGeneratedFolders = listOf(generatedTestSourceFolder)),
        RootTestData(expectedPath = optionalGeneratedSourceFolder, expectedMainGeneratedFolders = listOf(optionalGeneratedSourceFolder)),
        RootTestData(expectedPath = optionalGeneratedTestSourceFolder,
                     expectedTestGeneratedFolders = listOf(optionalGeneratedTestSourceFolder)))
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

    val contentRoots = collect(contentRoots = listOf(baseContentRoot),
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
                               mainOptionalGeneratedFolders = listOf(annotationProcessorDirectory),
                               testOptionalGeneratedFolders = listOf(annotationProcessorTestDirectory))

    assertContentRoots(contentRoots, listOf(
      RootTestData(expectedPath = baseContentRoot,
                   expectedMainSourceFolders = listOf(sourceMain),
                   expectedMainResourcesFolders = listOf(resourceMain)),
      RootTestData(expectedPath = sourceMain2, expectedMainSourceFolders = listOf(sourceMain2)),
      RootTestData(expectedPath = sourceMain3, expectedMainSourceFolders = listOf(sourceMain3)),
      RootTestData(expectedPath = sourceMain4, expectedMainSourceFolders = listOf(sourceMain4)),
      RootTestData(expectedPath = resourceMain2, expectedMainResourcesFolders = listOf(resourceMain2)),
      RootTestData(expectedPath = resourceMain3, expectedMainResourcesFolders = listOf(resourceMain3)),
      RootTestData(expectedPath = resourceMain4, expectedMainResourcesFolders = listOf(resourceMain4)),
      RootTestData(expectedPath = annotationProcessorDirectory,
                   expectedMainGeneratedFolders = listOf(annotationProcessorDirectory)),
      RootTestData(expectedPath = annotationProcessorTestDirectory,
                   expectedTestGeneratedFolders = listOf(annotationProcessorTestDirectory)))
    )
  }

  fun collect(contentRoots: List<String> = emptyList(),
              mainSourceFolders: List<String> = emptyList(),
              mainResourceFolders: List<String> = emptyList(),
              testSourceFolders: List<String> = emptyList(),
              testResourceFolders: List<String> = emptyList(),
              excludeFolders: List<String> = emptyList(),
              excludeNoSubSourceFolders: List<String> = emptyList(),
              mainGeneratedSourceFolders: List<String> = emptyList(),
              testGeneratedSourceFolders: List<String> = emptyList(),
              mainOptionalGeneratedFolders: List<String> = emptyList(),
              testOptionalGeneratedFolders: List<String> = emptyList()): Collection<ContentRootCollector.Result> {
    val foldersData = mutableListOf<ContentRootCollector.Folder>()

    contentRoots.forEach { foldersData.add(ContentRootCollector.ContentRootFolder(it)) }

    mainSourceFolders.forEach { foldersData.add(ContentRootCollector.SourceFolder(it, JavaSourceRootType.SOURCE)) }
    mainResourceFolders.forEach { foldersData.add(ContentRootCollector.SourceFolder(it, JavaResourceRootType.RESOURCE)) }
    mainGeneratedSourceFolders.forEach {
      foldersData.add(ContentRootCollector.ExplicitGeneratedSourceFolder(it, JavaSourceRootType.SOURCE))
    }
    mainOptionalGeneratedFolders.forEach {
      foldersData.add(ContentRootCollector.OptionalGeneratedSourceFolder(it, JavaSourceRootType.SOURCE))
    }

    testSourceFolders.forEach { foldersData.add(ContentRootCollector.SourceFolder(it, JavaSourceRootType.TEST_SOURCE)) }
    testResourceFolders.forEach { foldersData.add(ContentRootCollector.SourceFolder(it, JavaResourceRootType.TEST_RESOURCE)) }
    testGeneratedSourceFolders.forEach {
      foldersData.add(ContentRootCollector.ExplicitGeneratedSourceFolder(it, JavaSourceRootType.TEST_SOURCE))
    }
    testOptionalGeneratedFolders.forEach {
      foldersData.add(ContentRootCollector.OptionalGeneratedSourceFolder(it, JavaSourceRootType.TEST_SOURCE))
    }

    excludeFolders.forEach { foldersData.add(ContentRootCollector.ExcludedFolder(it)) }
    excludeNoSubSourceFolders.forEach { foldersData.add(ContentRootCollector.ExcludedFolderAndPreventGeneratedSubfolders(it)) }

    return collect(foldersData)
  }

  private fun assertContentRoots(actualRoots: Collection<ContentRootCollector.Result>,
                                 expectedRoots: Collection<RootTestData>) {
    fun mapPaths(result: ContentRootCollector.Result,
                 clazz: Class<out ContentRootCollector.UserOrGeneratedSourceFolder>,
                 type: JpsModuleSourceRootType<*>) =
      result.folders.filterIsInstance(clazz).filter { it.type == type }.map { it.path }.sorted()

    val actualSorted = actualRoots.map {
      RootTestData(
        it.path,
        expectedMainSourceFolders = mapPaths(it, ContentRootCollector.SourceFolder::class.java, JavaSourceRootType.SOURCE),
        expectedMainResourcesFolders = mapPaths(it, ContentRootCollector.SourceFolder::class.java, JavaResourceRootType.RESOURCE),
        expectedMainGeneratedFolders = mapPaths(it, ContentRootCollector.BaseGeneratedSourceFolder::class.java, JavaSourceRootType.SOURCE),

        expectedTestSourceFolders = mapPaths(it, ContentRootCollector.SourceFolder::class.java, JavaSourceRootType.TEST_SOURCE),
        expectedTestResourcesFolders = mapPaths(it, ContentRootCollector.SourceFolder::class.java, JavaResourceRootType.TEST_RESOURCE),
        expectedTestGeneratedFolders = mapPaths(it, ContentRootCollector.BaseGeneratedSourceFolder::class.java,
                                                JavaSourceRootType.TEST_SOURCE),

        expectedExcludes = it.folders.filterIsInstance<ContentRootCollector.BaseExcludedFolder>().map { it.path }.sorted(),
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

  private data class RootTestData(val expectedPath: String,
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