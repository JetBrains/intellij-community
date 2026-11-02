// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.URLUtil
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.builders.BuildResult
import org.jetbrains.jps.builders.CompileScopeTestBuilder
import org.jetbrains.jps.builders.JpsBuildTestBase
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl
import org.jetbrains.jps.builders.logging.BuildLoggingManager
import org.jetbrains.jps.incremental.BuilderRegistry
import org.jetbrains.jps.incremental.IncProjectBuilder
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.sdk.JpsSdk
import org.jetbrains.jps.util.JpsPathUtil
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.config.IncrementalCompilation
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.jps.model.JpsKotlinFacetModuleExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import java.io.File

/**
 * Runs an incremental test case of the Kotlin repository through the Build Tools API builder.
 *
 * The case comes from [KotlinJpsTestData]. The test makes the initial build, applies each step, and makes an incremental
 * build after it. It compares the log of the steps with the golden file, see [KotlinJpsBuildLogger]. Then it compares
 * the output with the output of a clean rebuild.
 *
 * The cases of the legacy builder that check its own caches or lookups have no counterpart here. The builder keeps no
 * caches, and JPS owns the incremental compilation.
 */
abstract class AbstractKotlinJpsIncrementalTest : JpsBuildTestBase() {
  private var workDir: File? = null

  /** The implementation that the cases compile with. A subclass runs the same cases with another one. */
  protected open val btaImplementation: KotlinBtaImplementation
    get() = KotlinBtaImplementation.CURRENT

  override fun shouldRunTest(): Boolean = super.shouldRunTest() && !btaImplementation.duplicatesCurrent

  @BeforeEach
  fun setUpKotlinJpsIncrementalTest() {
    System.setProperty(GlobalOptions.DEPENDENCY_GRAPH_ENABLED, "true")
    System.setProperty(IncrementalCompilation.INCREMENTAL_COMPILATION_JVM_PROPERTY, "true")
    btaImplementation.install()
  }

  override fun doGetProjectDir(): File = workDir ?: super.doGetProjectDir()

  protected fun runTest(relativePath: String) {
    val compilerVersion = btaImplementation.compilerVersion
    val tempRoot = createTempDirectory("kotlin-jps-incremental")
    val caseDir = File(tempRoot, "case")
    KotlinJpsTestData.materialize(relativePath, caseDir.toPath())

    KotlinJpsTestData.mutedReason(caseDir.toPath(), compilerVersion)?.let { reason ->
      println("$relativePath is muted for $compilerVersion: $reason")
      return
    }
    val project = KotlinJpsTestProject.read(caseDir)
    if (project?.muted == true) return

    val workDir = File(tempRoot, "work").also { it.mkdirs() }
    this.workDir = workDir
    configureProject(caseDir, project)

    val initialMake = build(CompileScopeTestBuilder.make().allModules(), null)
    assertFalse(initialMake.failed, "Initial make failed:\n${initialMake.log}")

    val steps = KotlinJpsModifications.stepsOf(caseDir, project?.modules)
    if (steps.isEmpty()) return

    val stepNames = File(caseDir, "_steps.txt").takeIf { it.isFile }?.readLines()
    val results = steps.mapIndexed { index, step ->
      for (modification in step) {
        modification.perform(workDir)
      }
      build(CompileScopeTestBuilder.make().allModules(), stepNames?.getOrNull(index))
    }

    // The rebuild check runs after a golden mismatch too: it finds the stale outputs, which the golden file cannot show
    val goldenMismatch = runCatching {
      KotlinJpsTestData.assertGolden(
        relativePath = relativePath,
        caseDir = caseDir.toPath(),
        names = KotlinJpsTestData.goldenNames(JPS_LOG, compilerVersion, listOf(JPS_LOG, FIR_LOG, SIMPLE_LOG)),
        overwriteName = JPS_LOG,
        actual = buildLog(results),
        normalize = ::excludeCompilerErrorMessages,
      )
    }.exceptionOrNull()

    clearCachesRebuildAndCheckOutput(results.last())
    goldenMismatch?.let { throw it }
  }

  private fun configureProject(caseDir: File, project: KotlinJpsTestProject?) {
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(myProject).outputUrl = JpsPathUtil.pathToUrl(getAbsolutePath("out"))

    val jdk = addModularJdk()
    if (project == null) {
      addModule("module", arrayOf(getAbsolutePath("src")), null, null, jdk)
      KotlinJpsModifications.copyTestSources(caseDir, File(workDir, "src"), "")
    }
    else {
      val modules = project.modules.associateWith { name -> addModule(name, arrayOf(getAbsolutePath("$name/src")), null, null, jdk) }
      for (dependency in project.dependencies) {
        val moduleDependency = modules.getValue(dependency.from).dependenciesList.addModuleDependency(modules.getValue(dependency.to))
        JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(moduleDependency).apply {
          scope = dependency.scope
          isExported = dependency.exported
        }
      }
      for (name in project.modules) {
        KotlinJpsModifications.copyTestSources(caseDir, File(workDir, "$name/src"), "${name}_")
      }
    }

    val additionalArguments = File(caseDir, "args.txt").takeIf { it.isFile }?.readText()?.split(" ", "\n")?.filter { it.isNotBlank() }
    if (additionalArguments != null) {
      for (module in myProject.modules) {
        val facet = KotlinFacetSettings()
        facet.useProjectSettings = false
        facet.compilerSettings = CompilerSettings().apply { this.additionalArguments = additionalArguments.joinToString(" ") }
        module.container.setChild(JpsKotlinFacetModuleExtension.KIND, JpsKotlinFacetModuleExtension(facet))
      }
    }

    val stdlib = addLibrary("kotlin-stdlib", btaImplementation.stdlibRoots)
    val kotlinTest = addLibrary("kotlin-test", btaImplementation.kotlinTestRoots)
    for (module in myProject.modules) {
      module.dependenciesList.addLibraryDependency(stdlib)
      module.dependenciesList.addLibraryDependency(kotlinTest)
    }
  }

  /** A JDK of the test JVM with `jrt://` roots, as the IDE registers a modular JDK. */
  private fun addModularJdk(): JpsSdk<JpsDummyElement> {
    val javaHome = FileUtil.toSystemIndependentName(System.getProperty("java.home"))
    val jdk = myModel.global.addSdk("my jdk", javaHome, System.getProperty("java.version"), JpsJavaSdkType.INSTANCE)
    jdk.addRoot(URLUtil.JRT_PROTOCOL + URLUtil.SCHEME_SEPARATOR + javaHome + URLUtil.JAR_SEPARATOR + "java.base", JpsOrderRootType.COMPILED)
    return jdk.properties
  }

  private fun addLibrary(name: String, roots: List<File>): JpsLibrary {
    val library = myProject.addLibrary(name, JpsJavaLibraryType.INSTANCE)
    for (root in roots) {
      library.addRoot(root.toPath(), JpsOrderRootType.COMPILED)
    }
    return library
  }

  private class MakeResult(val log: String, val failed: Boolean, val name: String?)

  private fun build(scope: CompileScopeTestBuilder, name: String?): MakeResult {
    val logger = KotlinJpsBuildLogger(FileUtil.toSystemIndependentName(workDir!!.canonicalPath))
    val descriptor = createProjectDescriptor(BuildLoggingManager(logger))
    try {
      val builder = IncProjectBuilder(descriptor, BuilderRegistry.getInstance(), myBuildParams, CanceledStatus.NULL, true)
      val buildResult = BuildResult()
      builder.addMessageHandler(buildResult)
      builder.build(scope.build(), false)

      if (buildResult.isSuccessful) return MakeResult(logger.log, false, name)

      val errors = buildResult.getMessages(BuildMessage.Kind.ERROR).joinToString("\n") { it.messageText.replace(ERROR_LOCATION, "").trim() }
      return MakeResult(logger.log + "$COMPILATION_FAILED\n" + errors + "\n", true, name)
    }
    finally {
      descriptor.dataManager.flush(false)
      descriptor.release()
    }
  }

  private fun buildLog(results: List<MakeResult>): String = buildString {
    results.forEachIndexed { index, result ->
      if (index > 0) append("\n")
      val title = if (result.name != null) "Step #${index + 1} ${result.name}" else "Step #${index + 1}"
      append("================ $title =================\n\n")
      append(result.log)
    }
  }

  /** The compiler versions word their errors differently, so the log keeps only the fact of the failure. */
  private fun excludeCompilerErrorMessages(log: String): String {
    if (COMPILATION_FAILED !in log) return log
    return log.split(COMPILATION_FAILED).mapIndexed { index, part ->
      if (index == 0) part else if (part.indexOf("=") > 0) part.substring(part.indexOf("=")) else ""
    }.joinToString("$COMPILATION_FAILED\n\n")
  }

  private fun clearCachesRebuildAndCheckOutput(lastMake: MakeResult) {
    FileUtil.delete(BuildDataPathsImpl(myDataStorageRoot.toPath()).dataStorageDir.toFile())

    val outDir = File(getAbsolutePath("out"))
    val outAfterMake = File(getAbsolutePath("out-after-make"))
    if (outDir.exists()) {
      FileUtil.copyDir(outDir, outAfterMake)
    }

    val rebuild = build(CompileScopeTestBuilder.rebuild().allModules(), null)
    assertEquals(lastMake.failed, rebuild.failed, "Rebuild failed: ${rebuild.failed}, last make failed: ${lastMake.failed}. Rebuild log:\n${rebuild.log}")

    if (!outAfterMake.exists()) {
      assertFalse(outDir.exists())
    }
    else {
      KotlinJpsOutputComparison.assertEqualDirectories(outDir, outAfterMake, forgiveExtraFiles = lastMake.failed)
    }
    FileUtil.delete(outAfterMake)
  }

  private companion object {
    const val JPS_LOG = "jps-build.log"
    const val FIR_LOG = "fir-build.log"
    const val SIMPLE_LOG = "build.log"
    const val COMPILATION_FAILED = "COMPILATION FAILED"
    val ERROR_LOCATION = Regex("^.+:\\d+:\\s+")
  }
}
