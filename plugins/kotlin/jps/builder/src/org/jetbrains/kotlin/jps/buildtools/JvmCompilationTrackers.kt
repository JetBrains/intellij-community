// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.buildtools

import org.jetbrains.jps.builders.java.dependencyView.Callbacks
import org.jetbrains.kotlin.buildtools.api.jps.jvm.incremental.trackers.CompilerEnumWhenTracker
import org.jetbrains.kotlin.buildtools.api.jps.jvm.incremental.trackers.CompilerFileMappingTracker
import org.jetbrains.kotlin.buildtools.api.jps.jvm.incremental.trackers.CompilerImportTracker
import org.jetbrains.kotlin.buildtools.api.jps.jvm.incremental.trackers.CompilerInlineConstTracker
import org.jetbrains.kotlin.buildtools.api.trackers.CompilerLookupTracker
import org.jetbrains.kotlin.jps.build.KotlinBuilder
import java.io.File
import java.nio.file.Path

/**
 * What the compiler reports about one compilation, collected for the JPS dependency graph.
 *
 * The builder keeps no state of its own between builds. Everything here goes to the dependency graph of JPS right after
 * the compilation, and JPS decides what to recompile next time.
 */
class JvmCompilationTrackers {
  val lookups: LookupCollector = LookupCollector()
  val inlineConstants: InlineConstantCollector = InlineConstantCollector()
  val enumWhens: EnumWhenCollector = EnumWhenCollector()
  val imports: ImportCollector = ImportCollector()
  val outputs: OutputCollector = OutputCollector()
}

/** A symbol that a source file looked up. The graph stores it as a `LookupNameUsage` of the source. */
class LookupCollector : CompilerLookupTracker {
  class LookupKey(val scopeInternalName: String, val name: String) {
    override fun equals(other: Any?): Boolean =
      this === other || (other is LookupKey && scopeInternalName == other.scopeInternalName && name == other.name)

    override fun hashCode(): Int = 31 * scopeInternalName.hashCode() + name.hashCode()
  }

  private val mutableLookups = HashMap<LookupKey, MutableSet<String>>()

  /** Lookup key to the paths of the source files that made the lookup. */
  val lookups: Map<LookupKey, Set<String>>
    get() = mutableLookups

  override fun recordLookup(filePath: String, scopeFqName: String, scopeKind: CompilerLookupTracker.ScopeKind, name: String) {
    mutableLookups.getOrPut(LookupKey(scopeFqName.replace('.', '/'), name)) { HashSet() }.add(filePath)
  }

  override fun clear() {
    mutableLookups.clear()
  }
}

/** The compile-time constants a source file inlined, keyed by the source path. */
class InlineConstantCollector : CompilerInlineConstTracker {
  private val mutableReferences = HashMap<String, MutableList<Callbacks.ConstantRef>>()

  val referencesBySource: Map<String, List<Callbacks.ConstantRef>>
    get() = mutableReferences

  override fun report(filePath: Path, owner: String, name: String, constType: String) {
    val descriptor = when (constType) {
      "Byte" -> "B"
      "Short" -> "S"
      "Int" -> "I"
      "Long" -> "J"
      "Float" -> "F"
      "Double" -> "D"
      "Boolean" -> "Z"
      "Char" -> "C"
      "String" -> "Ljava/lang/String;"
      else -> return
    }
    mutableReferences.getOrPut(filePath.toString()) { ArrayList() }.add(Callbacks.createConstantReference(owner, name, descriptor))
  }
}

/** The enum classes a source file matches exhaustively in a `when`, keyed by the source path. */
class EnumWhenCollector : CompilerEnumWhenTracker {
  private val mutableEnumClasses = HashMap<String, MutableSet<String>>()

  /** The graph takes an enum class as a star import of the class. */
  val enumClassImportsBySource: Map<String, Set<String>>
    get() = mutableEnumClasses

  override fun report(whenExpressionFilePath: Path, enumClassFqName: String) {
    mutableEnumClasses.getOrPut(whenExpressionFilePath.toString()) { LinkedHashSet() }.add("$enumClassFqName.*")
  }
}

/** The imports of a source file, keyed by the source path. */
class ImportCollector : CompilerImportTracker {
  private val mutableImports = HashMap<String, MutableSet<String>>()

  val importsBySource: Map<String, Set<String>>
    get() = mutableImports

  override fun report(filePath: Path, importedFqName: String) {
    mutableImports.getOrPut(filePath.toString()) { LinkedHashSet() }.add(importedFqName)
  }
}

/** The output files of the compilation with the source files they come from. */
class OutputCollector : CompilerFileMappingTracker {
  private val mutableOutputs = LinkedHashMap<File, MutableSet<File>>()

  val outputs: Map<File, Set<File>>
    get() = mutableOutputs

  override fun recordSourceFilesToOutputFileMapping(sourceFilePaths: Collection<Path>, outputFilePath: Path) {
    mutableOutputs.getOrPut(outputFilePath.toFile().absoluteFile) { LinkedHashSet() }.addAll(sourceFilePaths.map { it.toFile() })
  }

  override fun recordSourceReferencedByCompilerPlugin(sourceFilePath: Path) {
    KotlinBuilder.LOG.debug("A compiler plugin referenced $sourceFilePath")
  }

  override fun recordOutputFileGeneratedForPlugin(outputFilePath: Path) {
    KotlinBuilder.LOG.debug("A compiler plugin generated the output $outputFilePath")
  }

  override fun recordSourceFileGeneratedForPlugin(sourceFilePath: Path) {
    KotlinBuilder.LOG.debug("A compiler plugin generated the source $sourceFilePath")
  }
}
