// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.dependency.java.LookupNameUsage
import org.jetbrains.jps.incremental.BinaryContent
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.CompiledClass
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.kotlin.jps.buildtools.JvmCompilationOutcome
import org.jetbrains.kotlin.metadata.jvm.deserialization.ModuleMapping
import org.jetbrains.org.objectweb.asm.ClassReader
import java.io.File
import java.nio.file.Paths

/**
 * Hands the result of one compilation to JPS: the output files to the output consumer, and what the compiler reported
 * to the dependency graph. The graph then computes the files to recompile on the next change.
 */
internal class DependencyGraphRegistrar(
  private val context: CompileContext,
  private val outputConsumer: ModuleLevelBuilder.OutputConsumer,
  private val instrumentBytecode: Boolean,
) {
  fun register(target: ModuleBuildTarget, outcome: JvmCompilationOutcome) {
    val callback = JavaBuilderUtil.getDependenciesRegistrar(context)
    val trackers = outcome.trackers

    // The graph accepts a usage of a source only before the outputs of the source are associated
    for ((key, sources) in trackers.lookups.lookups) {
      val usage = LookupNameUsage(key.scopeInternalName, key.name)
      for (source in sources) {
        callback.registerUsage(Paths.get(source), usage)
      }
    }

    for ((outputFile, sourceFiles) in trackers.outputs.outputs) {
      val sourcePaths = sourceFiles.map { it.path }
      when {
        outputFile.name.endsWith(".class") -> {
          val bytes = outputFile.readBytes()
          val reader = ClassReader(bytes)
          val className = reader.className

          for (source in sourceFiles) {
            trackers.inlineConstants.referencesBySource[source.path]?.let { callback.registerConstantReferences(className, it) }
            val imports = trackers.imports.importsBySource[source.path]
            val enumImports = trackers.enumWhens.enumClassImportsBySource[source.path]
            if (imports != null || enumImports != null) {
              callback.registerImports(className, imports.orEmpty(), staticImportsOf(imports, enumImports))
            }
          }

          if (instrumentBytecode) {
            outputConsumer.registerCompiledClass(target, CompiledClass(outputFile, sourceFiles, className, BinaryContent(bytes)))
          }
          else {
            outputConsumer.registerOutputFile(target, outputFile, sourcePaths)
          }
          callback.associate(
            FileUtil.toSystemIndependentName(outputFile.normalize().absolutePath),
            sourcePaths.map { FileUtil.toSystemIndependentName(File(it).normalize().absolutePath) },
            reader,
          )
        }
        else -> outputConsumer.registerOutputFile(target, outputFile, sourcePaths)
      }
    }

    // The next compilation of a few sources needs this mapping, and JPS deletes the file with the outputs of these sources
    val storage = KotlinModuleMappingStorage.of(context, target)
    val mappingFile = File(target.outputDir, "META-INF").listFiles()?.firstOrNull { it.name.endsWith(".${ModuleMapping.MAPPING_FILE_EXT}") }
    if (mappingFile != null) storage.write(mappingFile.readBytes()) else storage.clean()
  }

  /**
   * The imports to report as static imports: the enum classes of the exhaustive `when` expressions of the source, and
   * the imports that can bring in a static member.
   *
   * The compiler reports the text of an import directive. It does not report what the directive resolved to. A Kotlin
   * import has no `static` form. An import of `a.b.C.m` can name a nested class or a static member. A star import of
   * `a.b.C` can bring in nested classes and static members both. JPS turns a static import into a `ClassUsage` of the
   * owner and an `ImportStaticMemberUsage` or an `ImportStaticOnDemandUsage` of the member. A change of the member then
   * recompiles the source. A name without a dot has no owner, and JPS cannot turn it into a member import, so such a
   * name is left out.
   */
  private fun staticImportsOf(imports: Collection<String>?, enumImports: Collection<String>?): Collection<String> {
    val staticImports = LinkedHashSet<String>()
    enumImports?.let(staticImports::addAll)
    imports?.forEach { importedFqName ->
      if (importedFqName.lastIndexOf('.') > 0) staticImports.add(importedFqName)
    }
    return staticImports
  }
}
