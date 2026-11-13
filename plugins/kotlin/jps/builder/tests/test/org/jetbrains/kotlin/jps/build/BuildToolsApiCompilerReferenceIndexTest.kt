// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.builders.impl.BuildDataPathsImpl
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.jetbrains.kotlin.buildtools.api.getToolchain
import org.jetbrains.kotlin.config.SettingConstants.KOTLIN_COMPILER_REFERENCE_INDEX_BUILDER_ID
import org.jetbrains.kotlin.jps.buildtools.KotlinBuildToolsLoader
import org.jetbrains.kotlin.name.FqName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize

/**
 * Checks the compiler reference index that a JPS build through the Build Tools API writes.
 *
 * The index is the `cri` directory under the data root of each compiled target. [CriToolchain] names its files. The
 * builder attaches the index configuration to every compilation, so the loaded implementation writes the tables.
 */
@OptIn(ExperimentalBuildToolsApi::class)
class BuildToolsApiCompilerReferenceIndexTest : KotlinBtaJpsBuildTestBase() {
  private val dataPaths by lazy { BuildDataPathsImpl(myDataStorageRoot.toPath()) }

  @Test
  fun testIndexIsWrittenForEachModuleTarget() {
    val modules = createProject()
    buildAllModules().assertSuccessful()

    for (module in modules) {
      for (fileName in CRI_FILE_NAMES) {
        val table = criDirectory(module).resolve(fileName)
        assertTrue(table.exists()) { "No compiler reference index file $fileName for ${module.name} at $table" }
      }
    }
  }

  @Test
  fun testReadbackReturnsTheCrossModuleReference() {
    val modules = createProject()
    buildAllModules().assertSuccessful()

    val referencingPaths = referencingPaths(FqName("a.Base"), criDirectory(modules[1]))

    assertEquals(
      listOf(File(getAbsolutePath("b/src/Derived.kt")).canonicalPath),
      referencingPaths.map { File(it).canonicalPath },
    )
  }

  @Test
  fun testAnImplementationWithoutTheIndexStillBuilds() {
    assumeFalse(KotlinBtaImplementation.STABLE.duplicatesBootstrap)
    btaImplementation = KotlinBtaImplementation.STABLE
    btaImplementation.install()
    val modules = createProject()
    val result = buildAllModules()
    result.assertSuccessful()

    // The flag carries the claim of the one warning, whatever build of this JVM hit the rejection first
    assertTrue(KotlinBuildToolsLoader.isCompilerRefIndexUnsupported())

    for (module in modules) {
      assertFalse(criDirectory(module).exists()) { "Unexpected compiler reference index for ${module.name}" }
    }
  }

  @Test
  fun testAFullyCompiledModuleReportsItselfProcessed() {
    val modules = createProject()
    val result = buildAllModules()
    result.assertSuccessful()

    val processedMessages = result.getMessages(BuildMessage.Kind.INFO)
      .filterIsInstance<CustomBuilderMessage>()
      .filter { it.builderId == KOTLIN_COMPILER_REFERENCE_INDEX_BUILDER_ID }
    assertEquals(modules.map { it.name }.sorted(), processedMessages.map { it.messageText }.sorted())
    assertEquals(listOf("processed module"), processedMessages.map { it.messageType }.distinct())
  }

  @Test
  fun testRebuildStartsTheIndexOver() {
    val modules = createProject()
    buildAllModules().assertSuccessful()
    val afterBuild = lookupsSizes(modules)
    val derivedReferences = referencingPaths(FqName("a.Base"), criDirectory(modules[1]))

    // An incremental build appends to the tables, so the rebuild below has stale data to clear
    changeFile("b/src/Derived.kt", "package b\n\nclass Derived : a.Base() { fun x() = 1 }\n")
    buildAllModules().assertSuccessful()
    assertNotEquals(afterBuild, lookupsSizes(modules)) { "The incremental build must change the index" }
    rebuildAllModules()

    assertEquals(afterBuild, lookupsSizes(modules)) { "A rebuild must clear the data of the earlier builds" }
    assertEquals(
      derivedReferences,
      referencingPaths(FqName("a.Base"), criDirectory(modules[1])),
      "A rebuild must keep the references of the sources",
    )
  }

  private fun createProject(): List<JpsModule> {
    createFile("a/src/Base.kt", "package a\n\nopen class Base\n")
    val moduleA = addModuleOnModularJdk("a", getAbsolutePath("a/src"))
    createFile("b/src/Derived.kt", "package b\n\nclass Derived : a.Base()\n")
    val moduleB = addModuleOnModularJdk("b", getAbsolutePath("b/src"))
    moduleB.dependenciesList.addModuleDependency(moduleA)
    addKotlinStdlib(moduleA, moduleB)
    return listOf(moduleA, moduleB)
  }

  private fun criDirectory(module: JpsModule): Path =
    dataPaths.getTargetDataRootDir(ModuleBuildTarget(module, false)).resolve(CriToolchain.DATA_PATH)

  private fun lookupsSizes(modules: List<JpsModule>): List<Long> =
    modules.map { criDirectory(it).resolve(CriToolchain.LOOKUPS_FILENAME).fileSize() }

  /**
   * Reads the tables of [criDirectory] through the deserialization operations and returns the paths of the files that
   * reference [fqName].
   */
  private fun referencingPaths(fqName: FqName, criDirectory: Path): List<String> {
    val toolchains = KotlinBuildToolsLoader.load() ?: error("No Build Tools API implementation loaded")
    val cri = toolchains.getToolchain<CriToolchain>()
    toolchains.createBuildSession().use { session ->
      val lookups = session.executeOperation(
        cri.createCriLookupDataDeserializationOperation(Files.readAllBytes(criDirectory.resolve(CriToolchain.LOOKUPS_FILENAME)))
      )
      val paths = session.executeOperation(
        cri.createCriFileIdToPathDataDeserializationOperation(Files.readAllBytes(criDirectory.resolve(CriToolchain.FILE_IDS_TO_PATHS_FILENAME)))
      )
      val fileIds = lookups.filter { it.fqNameHashCode == fqName.hashCode() }.flatMap { it.fileIds }
      return fileIds.mapNotNull { fileId -> paths.firstOrNull { it.fileId == fileId }?.path }
    }
  }

  private companion object {
    val CRI_FILE_NAMES = listOf(
      CriToolchain.LOOKUPS_FILENAME,
      CriToolchain.FILE_IDS_TO_PATHS_FILENAME,
      CriToolchain.SUBTYPES_FILENAME,
    )
  }
}
