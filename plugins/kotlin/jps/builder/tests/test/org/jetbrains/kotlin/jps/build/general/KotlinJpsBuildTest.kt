// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build.general

import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.builders.BuildResult
import org.jetbrains.jps.builders.TestProjectBuilderLogger
import org.jetbrains.jps.builders.impl.logging.ProjectBuilderLoggerBase
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.kotlin.jps.build.KotlinBuilderTestingLogger
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.IncrementalCompilation
import org.jetbrains.kotlin.config.JvmDefaultMode
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.jps.build.KotlinBuilder
import org.jetbrains.kotlin.jps.build.general.KotlinJpsBuildTestBase.LibraryDependency.JVM_FULL_RUNTIME
import org.jetbrains.kotlin.jps.build.general.KotlinJpsBuildTestBase.LibraryDependency.JVM_MOCK_RUNTIME
import org.jetbrains.kotlin.jps.model.JpsKotlinFacetModuleExtension
import org.jetbrains.kotlin.jps.model.kotlinCommonCompilerArguments
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URLClassLoader

/**
 * The JVM tests of `KotlinJpsBuildTest` in the Kotlin repository, on the Build Tools API builder.
 *
 * The tests of the legacy compiler runner, of Kotlin/JS, and of the own caches of the legacy builder are not here.
 * The tests that compile a library with the compiler of the test classpath, or that need a JDK 11 or a compiler plugin
 * jar, are not here either.
 */
open class KotlinJpsBuildTest : KotlinJpsBuildTestBase() {
  private fun doTest() {
    initProject(JVM_MOCK_RUNTIME)
    buildAllModules().assertSuccessful()
  }

  private fun doTestWithRuntime() {
    initProject(JVM_FULL_RUNTIME)
    buildAllModules().assertSuccessful()
  }

  @Test
  fun testKotlinProject() {
    doTest()

    checkWhen(createTouchAction("src/test1.kt"), null, packageClasses("kotlinProject", "src/test1.kt", "Test1Kt"))
  }

  @Test
  fun testSourcePackagePrefix() {
    doTest()
  }

  @Test
  fun testSourcePackageLongPrefix() {
    initProject(JVM_MOCK_RUNTIME)
    val buildResult = buildAllModules()
    buildResult.assertSuccessful()
    val warnings = buildResult.getMessages(BuildMessage.Kind.WARNING)
    assertEquals(1, warnings.size, "Warning about invalid package prefix in module 2 is expected: $warnings")
    assertEquals("Invalid package prefix name is ignored: invalid-prefix.test", warnings.first().messageText)
  }

  @Test
  fun testSourcePackagePrefixWithInnerClasses() {
    initProject(JVM_MOCK_RUNTIME)
    buildAllModules().assertSuccessful()
  }

  @Test
  fun testSourcePackagePrefixMixedRoots() {
    initProject(JVM_MOCK_RUNTIME)
    buildAllModules().assertSuccessful()
  }

  @Test
  fun testSourcePackagePrefixSameRoots() {
    initProject(JVM_MOCK_RUNTIME)
    buildAllModules().assertSuccessful()
  }

  @Test
  fun testJavaSourceRootsArgumentsIgnored() {
    initProject(JVM_MOCK_RUNTIME)
    val result = buildAllModules()
    result.assertSuccessful()

    val warnings = result.getMessages(BuildMessage.Kind.WARNING).map { it.messageText }
    val expected = "Argument '-Xjava-source-roots' is passed multiple times:"
    assertTrue(warnings.any { it.startsWith(expected) }) { "Expected a warning that starts with \"$expected\", got: $warnings" }
  }

  @Test
  fun testExcludeFolderInSourceRoot() {
    doTest()

    val module = myProject.modules[0]
    assertFilesExistInOutput(module, "Foo.class")
    assertFilesNotExistInOutput(module, *EXCLUDE_FILES)

    checkWhen(createTouchAction("src/foo.kt"), null, arrayOf(klass("kotlinProject", "Foo"), module("kotlinProject")))
  }

  @Test
  fun testExcludeModuleFolderInSourceRootOfAnotherModule() {
    doTest()

    for (module in myProject.modules) {
      assertFilesExistInOutput(module, "Foo.class")
    }

    checkWhen(createTouchAction("src/foo.kt"), null, arrayOf(klass("kotlinProject", "Foo"), module("kotlinProject")))
    checkWhen(createTouchAction("src/module2/src/foo.kt"), null, arrayOf(klass("module2", "Foo"), module("module2")))
  }

  @Test
  fun testExcludeFileUsingCompilerSettings() {
    doTest()

    val module = myProject.modules[0]
    assertFilesExistInOutput(module, "Foo.class", "Bar.class")
    assertFilesNotExistInOutput(module, *EXCLUDE_FILES)

    checkWhen(createTouchAction("src/foo.kt"), null, arrayOf(module("kotlinProject"), klass("kotlinProject", "Foo")))
    checkWhen(createTouchAction("src/Excluded.kt"), null, NOTHING)
    checkWhen(createTouchAction("src/dir/YetAnotherExcluded.kt"), null, NOTHING)
  }

  @Test
  fun testExcludeFolderNonRecursivelyUsingCompilerSettings() {
    doTest()

    val module = myProject.modules[0]
    assertFilesExistInOutput(module, "Foo.class", "Bar.class")
    assertFilesNotExistInOutput(module, *EXCLUDE_FILES)

    checkWhen(createTouchAction("src/foo.kt"), null, arrayOf(module("kotlinProject"), klass("kotlinProject", "Foo")))
    checkWhen(createTouchAction("src/dir/subdir/bar.kt"), null, arrayOf(module("kotlinProject"), klass("kotlinProject", "Bar")))
    checkWhen(createTouchAction("src/dir/Excluded.kt"), null, NOTHING)
    checkWhen(createTouchAction("src/dir/subdir/YetAnotherExcluded.kt"), null, NOTHING)
  }

  @Test
  fun testExcludeFolderRecursivelyUsingCompilerSettings() {
    doTest()

    val module = myProject.modules[0]
    assertFilesExistInOutput(module, "Foo.class", "Bar.class")
    assertFilesNotExistInOutput(module, *EXCLUDE_FILES)

    checkWhen(createTouchAction("src/foo.kt"), null, arrayOf(module("kotlinProject"), klass("kotlinProject", "Foo")))
    checkWhen(createTouchAction("src/exclude/Excluded.kt"), null, NOTHING)
    checkWhen(createTouchAction("src/exclude/YetAnotherExcluded.kt"), null, NOTHING)
    checkWhen(createTouchAction("src/exclude/subdir/Excluded.kt"), null, NOTHING)
    checkWhen(createTouchAction("src/exclude/subdir/YetAnotherExcluded.kt"), null, NOTHING)
  }

  @Test
  fun testKotlinProjectTwoFilesInOnePackage() {
    doTest()

    checkWhen(createTouchAction("src/test1.kt"), null, packageClasses("kotlinProject", "src/test1.kt", "_DefaultPackage"))
    checkWhen(createTouchAction("src/test2.kt"), null, packageClasses("kotlinProject", "src/test2.kt", "_DefaultPackage"))

    checkWhen(
      arrayOf(createDeleteAction("src/test1.kt"), createDeleteAction("src/test2.kt")), NOTHING,
      arrayOf(
        packagePartClass("kotlinProject", "src/test1.kt", "_DefaultPackage"),
        packagePartClass("kotlinProject", "src/test2.kt", "_DefaultPackage"),
        module("kotlinProject"),
      ),
    )

    assertFilesNotExistInOutput(myProject.modules[0], "_DefaultPackage.class")
  }

  @Test
  fun testDefaultLanguageVersionCustomApiVersion() {
    initProject(JVM_FULL_RUNTIME)
    buildAllModules().assertFailed()

    assertEquals(1, myProject.modules.size)
    myProject.kotlinCommonCompilerArguments.apiVersion = LanguageVersion.FIRST_API_SUPPORTED.versionString

    buildAllModules().assertSuccessful()
  }

  /** The legacy test checked that the builder returns early. Here the builder must compile no file. */
  @Test
  fun testPureJavaProject() {
    initProject(JVM_FULL_RUNTIME)

    buildAllModules().assertSuccessful()
    assertCompiled(KotlinBuilder.KOTLIN_BUILDER_NAME)

    rename("$workDir/src/Test.java", "Test1.java")
    buildAllModules().assertSuccessful()
    assertCompiled(KotlinBuilder.KOTLIN_BUILDER_NAME)
  }

  @Test
  fun testKotlinJavaProject() {
    doTestWithRuntime()
  }

  @Test
  fun testJKJProject() {
    doTestWithRuntime()
  }

  @Test
  fun testKJKProject() {
    doTestWithRuntime()
  }

  @Test
  fun testKJCircularProject() {
    doTestWithRuntime()
  }

  @Test
  fun testJKJInheritanceProject() {
    doTestWithRuntime()
  }

  @Test
  fun testKJKInheritanceProject() {
    doTestWithRuntime()
  }

  @Test
  fun testCircularDependencies() {
    initProject(JVM_MOCK_RUNTIME)
    val result = buildAllModules()

    result.assertFailed()
    result.checkErrors()
  }

  @Test
  fun testInternalFromAnotherModule() {
    initProject(JVM_MOCK_RUNTIME)
    val result = buildAllModules()
    result.assertFailed()
    result.checkErrors()
  }

  @Test
  fun testInternalFromSpecialRelatedModule() {
    initProject(JVM_MOCK_RUNTIME)
    buildAllModules().assertSuccessful()

    val classpath = listOf("out/production/module1", "out/test/module2").map { File(workDir, it).toURI().toURL() }.toTypedArray()
    val clazz = URLClassLoader(classpath).loadClass("test2.BarKt")
    clazz.getMethod("box").invoke(null)
  }

  @Test
  fun testDevKitProject() {
    initProject(JVM_MOCK_RUNTIME)
    val module = myProject.modules.single()
    buildAllModules().assertSuccessful()
    assertFilesExistInOutput(module, "TestKt.class")
  }

  @Test
  fun testAccessToInternalInProductionFromTests() {
    initProject(JVM_MOCK_RUNTIME)
    val result = buildAllModules()
    result.assertSuccessful()
  }

  @Test
  fun testReexportedDependency() {
    initProject()
    addKotlinStdlibDependency(myProject.modules.filter { module -> module.name == "module2" }, true)
    buildAllModules().assertSuccessful()
  }

  @Test
  fun testCheckIsCancelledIsCalledOftenEnough() {
    val classCount = 30
    val methodCount = 30

    val srcDir = File(workDir, "src")
    srcDir.mkdirs()
    for (i in 0..classCount) {
      val code = buildString {
        appendLine("package foo")
        appendLine("class Foo$i {")
        for (j in 0..methodCount) {
          appendLine("  fun get${j * j}(): Int = square($j)")
        }
        appendLine("}")
      }
      File(srcDir, "Foo$i.kt").writeText(code)
    }
    initProject(JVM_MOCK_RUNTIME)

    var checkCancelledCalledCount = 0
    val countingCancelledStatus = CanceledStatus {
      checkCancelledCalledCount++
      false
    }

    val buildResult = BuildResult()
    buildCustom(countingCancelledStatus, TestProjectBuilderLogger(), buildResult)

    buildResult.assertSuccessful()
    assertTrue(
      checkCancelledCalledCount > classCount,
      "isCancelled should be called at least once per class. Expected $classCount, but got $checkCancelledCalledCount",
    )
  }

  /**
   * The legacy test cancelled the build on the version message of the legacy compiler runner. The Build Tools API
   * reports no such message, so the test cancels the build when the Kotlin builder starts the chunk.
   */
  @Test
  fun testCancelKotlinCompilation() {
    initProject(JVM_MOCK_RUNTIME)
    buildAllModules().assertSuccessful()

    val module = myProject.modules[0]
    assertFilesExistInOutput(module, "foo/Bar.class")

    val buildResult = BuildResult()
    val logger = object : ProjectBuilderLoggerBase(), KotlinBuilderTestingLogger {
      @Volatile
      var kotlinChunkStarted = false

      override fun chunkBuildStarted(context: CompileContext, chunk: ModuleChunk) {
        kotlinChunkStarted = true
      }

      override fun buildFinished(exitCode: ModuleLevelBuilder.ExitCode) {}

      override fun isEnabled(): Boolean = true

      override fun logLine(message: String) {}
    }

    createTouchAction("src/Bar.kt").apply()
    buildCustom({ logger.kotlinChunkStarted }, logger, buildResult)
    val messages = buildResult.getMessages(BuildMessage.Kind.INFO)
    assertEquals("The build has been canceled", messages.last().messageText)
  }

  @Test
  fun testFileDoesNotExistWarning() {
    fun absoluteFiles(vararg paths: String): Array<File> = paths.map { File(it).absoluteFile }.toTypedArray()

    initProject(JVM_MOCK_RUNTIME)

    val filesToBeReported = absoluteFiles("badroot.jar", "some/test.class")
    val otherFiles = absoluteFiles("test/other/file.xml", "some/other/baddir")

    addDependency(
      JpsJavaDependencyScope.COMPILE,
      listOf(findModule("module")),
      false,
      "LibraryWithBadRoots",
      *(filesToBeReported + otherFiles),
    )

    val result = buildAllModules()
    result.assertSuccessful()

    val actualWarnings = result.getMessages(BuildMessage.Kind.WARNING).map { it.messageText }
    val expectedWarnings = filesToBeReported.map { "Classpath entry points to a non-existent location: $it" }
    assertEquals(expectedWarnings.sorted().joinToString("\n"), actualWarnings.sorted().joinToString("\n"))
  }

  @Test
  fun testWrongArgument() {
    initProject()

    val result = buildAllModules()
    result.assertFailed()
    val errors = result.getMessages(BuildMessage.Kind.ERROR).joinToString("\n\n") { it.messageText }
    assertEquals("Invalid argument: -abcdefghij-invalid-argument", errors)
  }

  @Test
  fun testCodeInKotlinPackage() {
    initProject(JVM_MOCK_RUNTIME)

    val result = buildAllModules()
    result.assertFailed()
    val errors = result.getMessages(BuildMessage.Kind.ERROR)
    assertEquals("Only the Kotlin standard library is allowed to use the 'kotlin' package.", errors.single().messageText)
  }

  @Test
  fun testDoNotCreateUselessKotlinIncrementalCaches() {
    initProject(JVM_MOCK_RUNTIME)
    buildAllModules().assertSuccessful()

    val storageRoot = BuildDataPathsImpl(myDataStorageRoot.toPath()).dataStorageDir.toFile()
    assertFalse(File(storageRoot, "targets/java-test/kotlinProject/kotlin").exists())
    assertFalse(File(storageRoot, "targets/java-production/kotlinProject/kotlin").exists())
  }

  @Test
  fun testDoNotCreateUselessKotlinIncrementalCachesForDependentTargets() {
    initProject(JVM_MOCK_RUNTIME)
    buildAllModules().assertSuccessful()

    checkWhen(createTouchAction("src/utils.kt"), null, packageClasses("kotlinProject", "src/utils.kt", "_DefaultPackage"))

    val storageRoot = BuildDataPathsImpl(myDataStorageRoot.toPath()).dataStorageDir.toFile()
    assertFalse(File(storageRoot, "targets/java-production/kotlinProject/kotlin").exists())
    assertFalse(File(storageRoot, "targets/java-production/module2/kotlin").exists())
  }

  @Test
  fun testKotlinProjectWithEmptyProductionOutputDir() {
    initProject(JVM_MOCK_RUNTIME)
    val result = buildAllModules()
    result.assertFailed()
    result.checkErrors()
  }

  @Test
  fun testKotlinProjectWithEmptyTestOutputDir() {
    doTest()
  }

  @Test
  fun testKotlinProjectWithEmptyProductionOutputDirWithoutSrcDir() {
    doTest()
  }

  @Test
  fun testKotlinProjectWithEmptyOutputDirInSomeModules() {
    doTest()
  }

  @Test
  fun testCustomDestination() {
    loadProject(workDir.absolutePath + File.separator + PROJECT_NAME + ".ipr")
    addKotlinStdlibDependency()
    val result = buildAllModules()
    result.assertSuccessful()

    val aClass = File(workDir, "customOut/A.class")
    assertTrue(aClass.exists(), "$aClass does not exist!")

    val warnings = result.getMessages(BuildMessage.Kind.WARNING)
    assertTrue(warnings.isEmpty(), "Unexpected warnings: \n${warnings.joinToString("\n")}")
  }

  @WorkingDir("KotlinProject")
  @Test
  fun testErrorWithoutDependencyGraph() {
    initProject(JVM_MOCK_RUNTIME)
    System.setProperty(GlobalOptions.DEPENDENCY_GRAPH_ENABLED, "false")
    try {
      val result = buildAllModules()
      result.assertFailed()
      val errors = result.getMessages(BuildMessage.Kind.ERROR).map { it.messageText }
      val expected = "The Kotlin JPS builder does not support these legacy modes: ${GlobalOptions.DEPENDENCY_GRAPH_ENABLED}=false."
      assertTrue(errors.any { it.startsWith(expected) }) { "Expected an error that starts with \"$expected\", got: $errors" }
    }
    finally {
      System.setProperty(GlobalOptions.DEPENDENCY_GRAPH_ENABLED, "true")
    }
  }

  @WorkingDir("KotlinProjectTwoFilesInOnePackage")
  @Test
  fun testCompileAllWithoutIncrementalCompilation() {
    doTest()

    System.setProperty(IncrementalCompilation.INCREMENTAL_COMPILATION_JVM_PROPERTY, "false")
    try {
      checkWhen(createTouchAction("src/test1.kt"), arrayOf("src/test1.kt", "src/test2.kt"), null)
    }
    finally {
      System.setProperty(IncrementalCompilation.INCREMENTAL_COMPILATION_JVM_PROPERTY, "true")
    }
  }

  @WorkingDir("KotlinProjectTwoFilesInOnePackage")
  @Test
  fun testCompileAllAfterAnotherCompilerRewritesModuleMapping() {
    doTest()

    File(workDir, module("kotlinProject")).appendBytes(byteArrayOf(0))
    // A new source has no outputs yet, so JPS keeps the module mapping until the builder sees it
    File(workDir, "src/test3.kt").writeText("fun test3() {}\n")
    buildAllModules().assertSuccessful()
    assertCompiled(KotlinBuilder.KOTLIN_BUILDER_NAME, "src/test1.kt", "src/test2.kt", "src/test3.kt")
  }

  @WorkingDir("KotlinProjectTwoFilesInOnePackage")
  @Test
  fun testCompileAllAfterLegacyPluginCompilesWithoutCaches() {
    doTest()

    // The legacy plugin rewrites the marker after each compilation, also without the incremental caches
    val marker = File(productionDataRoot(), "kotlin/has-kotlin-marker.txt")
    marker.parentFile.mkdirs()
    marker.writeText("true")
    marker.setLastModified(System.currentTimeMillis() + 60_000)

    checkWhen(createTouchAction("src/test1.kt"), arrayOf("src/test1.kt", "src/test2.kt"), null)
  }

  @WorkingDir("KotlinProjectTwoFilesInOnePackage")
  @Test
  fun testHandOverLegacyCaches() {
    doTest()

    // The caches that the legacy plugin leaves after it compiles the target
    val dataRoot = productionDataRoot()
    val cacheFile = File(dataRoot, "kotlin/proto.tab")
    cacheFile.parentFile.mkdirs()
    cacheFile.writeText("")
    File(dataRoot, "format-version.txt").writeText("16")
    val lookups = dataPaths().targetsDataRoot.resolve("kotlin-data-container").toFile()
    File(lookups, "kotlin-data-container/data-container-format-version.txt").apply { parentFile.mkdirs() }.writeText("7")

    checkWhen(createTouchAction("src/test1.kt"), arrayOf("src/test1.kt", "src/test2.kt"), null)

    assertFalse(cacheFile.exists(), "$cacheFile is not deleted")
    assertFalse(File(dataRoot, "format-version.txt").exists(), "The legacy cache version is not deleted")
    assertFalse(lookups.exists(), "$lookups is not deleted")
    assertEquals("true", File(dataRoot, "kotlin/has-kotlin-marker.txt").readText())

    // The handed over target is not compiled in full again
    checkWhen(createTouchAction("src/test1.kt"), arrayOf("src/test1.kt"), null)
  }

  private fun dataPaths(): BuildDataPathsImpl = BuildDataPathsImpl(myDataStorageRoot.toPath())

  private fun productionDataRoot(): File =
    dataPaths().getTargetDataRootDir(ModuleBuildTarget(myProject.modules.single(), JavaModuleBuildTargetType.PRODUCTION)).toFile()

  @WorkingDir("KotlinProject")
  @Test
  fun testModuleRebuildOnJvmTargetChange() {
    initProject(JVM_MOCK_RUNTIME)
    setJvmArguments { jvmTarget = "1.8" }
    buildAllModules().assertSuccessful()
    setJvmArguments { jvmTarget = "9" }

    checkWhen(emptyArray(), null, packageClasses("kotlinProject", "src/test1.kt", "Test1Kt"))
  }

  @WorkingDir("KotlinProject")
  @Test
  fun testModuleRebuildOnAllowNoSourceFilesRestriction() {
    // The rule gets stricter, so the module is compiled again
    initProject(JVM_MOCK_RUNTIME)
    setJvmArguments { allowNoSourceFiles = true }
    buildAllModules().assertSuccessful()
    setJvmArguments { allowNoSourceFiles = false }

    checkWhen(emptyArray(), null, packageClasses("kotlinProject", "src/test1.kt", "Test1Kt"))
  }

  @WorkingDir("KotlinProject")
  @Test
  fun testModuleNotRebuildOnAllowNoSourceFilesAllowance() {
    // The rule gets weaker, so the module is not compiled again
    initProject(JVM_MOCK_RUNTIME)
    setJvmArguments { allowNoSourceFiles = false }
    buildAllModules().assertSuccessful()
    setJvmArguments { allowNoSourceFiles = true }

    checkWhen(emptyArray(), null, NOTHING)
  }

  @WorkingDir("KotlinProject")
  @Test
  fun testModuleRebuildOnJvmDefaultChange() {
    initProject(JVM_MOCK_RUNTIME)
    setJvmArguments { jvmDefaultStable = JvmDefaultMode.DISABLE.description }
    buildAllModules().assertSuccessful()
    setJvmArguments { jvmDefaultStable = JvmDefaultMode.ENABLE.description }

    checkWhen(emptyArray(), null, packageClasses("kotlinProject", "src/test1.kt", "Test1Kt"))
  }

  @WorkingDir("KotlinProject")
  @Test
  fun testModuleRebuildOnAddJavaMoudlesChange() {
    initProject(JVM_MOCK_RUNTIME)
    setJvmArguments { }
    buildAllModules().assertSuccessful()
    setJvmArguments { additionalJavaModules = arrayOf("ALL-MODULE-PATH") }

    checkWhen(emptyArray(), null, packageClasses("kotlinProject", "src/test1.kt", "Test1Kt"))
  }

  @WorkingDir("KotlinProjectWithSingleKotlinFileAsSourceRoot")
  @Test
  fun testBuildProjectWithSingleKotlinFileAsSource() {
    initProject(JVM_MOCK_RUNTIME)
    buildAllModules().assertSuccessful()
  }

  /** Gives each module a facet with its own JVM arguments. */
  private fun setJvmArguments(configure: K2JVMCompilerArguments.() -> Unit) {
    for (module in myProject.modules) {
      val facet = KotlinFacetSettings()
      facet.useProjectSettings = false
      facet.compilerArguments = K2JVMCompilerArguments().apply(configure)
      module.container.setChild(JpsKotlinFacetModuleExtension.KIND, JpsKotlinFacetModuleExtension(facet))
    }
  }

  private companion object {
    val EXCLUDE_FILES = arrayOf("Excluded.class", "YetAnotherExcluded.class")
    val NOTHING = arrayOf<String>()
  }
}
