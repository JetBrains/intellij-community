// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.buildtools

import org.jetbrains.kotlin.buildtools.api.jps.jvm.incremental.CompilerIncrementalCache
import org.jetbrains.kotlin.buildtools.api.jps.jvm.incremental.CompilerIncrementalCompilationComponents
import org.jetbrains.kotlin.buildtools.api.jps.jvm.incremental.CompilerTargetId
import org.jetbrains.kotlin.jps.build.KotlinBuilder
import org.jetbrains.kotlin.metadata.jvm.deserialization.ModuleMapping
import java.io.File

/**
 * Describes the previous output of one target to the compiler.
 *
 * A compilation of a subset of the sources needs the module mapping (`META-INF/<module>.kotlin_module`) of the previous
 * compilation. The compiler keeps the parts that are not compiled again and adds the new ones. JPS deletes the mapping
 * file together with the outputs of a dirty or a removed source, so [previousMapping] comes from
 * `KotlinModuleMappingStorage`. It is `null` on the first compilation and after a rebuild.
 *
 * A part whose class file is missing is obsolete: JPS deleted it because its source is dirty or removed.
 */
class OutputDirectoryIncrementalCache(
  private val outputDirectory: File,
  moduleName: String,
  private val previousMapping: ByteArray?,
) : CompilerIncrementalCache {
  private val mappingFile = File(outputDirectory, "META-INF/$moduleName.${ModuleMapping.MAPPING_FILE_EXT}")

  private val mappingData: ByteArray?
    get() = previousMapping

  private val mapping: ModuleMapping by lazy {
    ModuleMapping.loadModuleMapping(
      mappingData,
      mappingFile.path,
      skipMetadataVersionCheck = true,
      isJvmPackageNameSupported = true,
    ) { version ->
      KotlinBuilder.LOG.warn("The module mapping $mappingFile has the incompatible metadata version $version")
    }
  }

  private val obsoleteParts: Set<String> by lazy {
    mapping.packageFqName2Parts.values
      .flatMap { it.parts }
      .filterTo(LinkedHashSet()) { !classFile(it).isFile }
  }

  override fun getObsoletePackageParts(): Collection<String> = obsoleteParts



  override fun getModuleMappingData(): ByteArray? = mappingData


  private fun classFile(internalName: String): File = File(outputDirectory, "$internalName.class")

}

/**
 * Hands the compiler the cache of the single target being compiled, whatever [CompilerTargetId] it asks for.
 *
 * Without `-Xbuild-file` the compiler has one module, named after `-module-name` and typed `java-production`. A test
 * target id does not round-trip, so the id is not matched.
 */
class SingleTargetIncrementalCompilationComponents(private val cache: CompilerIncrementalCache) : CompilerIncrementalCompilationComponents {
  override fun getIncrementalCache(target: CompilerTargetId): CompilerIncrementalCache = cache
}
