// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import java.io.File

/**
 * The modules of a multi-module case: the JVM subset of the `dependencies.txt` format of the Kotlin repository.
 *
 * A line `name [flags]` declares a module, and a line `from -> to [flags]` declares a dependency. `//` starts a comment.
 * The line `MUTED` mutes the case. The module flag `jvm` and the dependency flags `compile`, `test`, `runtime`,
 * `provided` and `exported` are supported. The multiplatform flags are not, because the builder compiles Kotlin/JVM only.
 */
internal class KotlinJpsTestProject(val muted: Boolean, val modules: List<String>, val dependencies: List<Dependency>) {
  class Dependency(val from: String, val to: String, val scope: JpsJavaDependencyScope, val exported: Boolean)

  /** @return `true` when the dependencies form a cycle, which the builder cannot compile. */
  fun hasCycle(): Boolean {
    val edges = dependencies.groupBy({ it.from }, { it.to })
    val visiting = HashSet<String>()
    val done = HashSet<String>()
    fun visit(module: String): Boolean {
      if (module in done) return false
      if (!visiting.add(module)) return true
      if (edges[module].orEmpty().any(::visit)) return true
      visiting.remove(module)
      done.add(module)
      return false
    }
    return modules.any(::visit)
  }

  companion object {
    private val UNSUPPORTED_MODULE_FLAGS = setOf("common", "js", "native", "sourceSetHolder", "compilationAndSourceSetHolder")
    private val IGNORED_MODULE_FLAGS = setOf("jvm", "edit", "editJvm", "editExpectActual")

    /** @return the modules of [caseDir], or `null` for a single module case. */
    fun read(caseDir: File): KotlinJpsTestProject? {
      val file = listOf("dependencies.txt", "_dependencies.txt").map { File(caseDir, it) }.firstOrNull { it.isFile } ?: return null
      try {
        return parse(file.readLines())
      }
      catch (e: Exception) {
        throw IllegalStateException("Cannot read $file: ${e.message}", e)
      }
    }

    private fun parse(lines: List<String>): KotlinJpsTestProject {
      var muted = false
      val modules = LinkedHashSet<String>()
      val dependencies = ArrayList<Dependency>()
      for (rawLine in lines) {
        val line = rawLine.substringBefore("//").trim()
        when {
          line.isEmpty() -> Unit
          line == "MUTED" -> muted = true
          "->" in line -> {
            val from = line.substringBefore("->").trim()
            val rest = line.substringAfter("->").trim()
            modules.add(from)
            if (rest.isEmpty()) continue
            val (to, flags) = valueWithFlags(rest)
            modules.add(to)
            dependencies.add(dependency(from, to, flags))
          }
          else -> {
            val (name, flags) = valueWithFlags(line)
            val unsupported = flags.filter { it in UNSUPPORTED_MODULE_FLAGS }
            check(unsupported.isEmpty()) { "Module $name: the flags $unsupported are not supported" }
            val unknown = flags.filter { it !in IGNORED_MODULE_FLAGS }
            check(unknown.isEmpty()) { "Module $name: unknown flags $unknown" }
            modules.add(name)
          }
        }
      }
      return KotlinJpsTestProject(muted, modules.toList(), dependencies)
    }

    private fun dependency(from: String, to: String, flags: Set<String>): Dependency {
      var scope: JpsJavaDependencyScope? = null
      var exported = false
      for (flag in flags) {
        val newScope = when (flag) {
          "exported" -> { exported = true; null }
          "compile" -> JpsJavaDependencyScope.COMPILE
          "test" -> JpsJavaDependencyScope.TEST
          "runtime" -> JpsJavaDependencyScope.RUNTIME
          "provided" -> JpsJavaDependencyScope.PROVIDED
          "expectedBy", "include" -> error("$from -> $to: the flag '$flag' is not supported")
          else -> error("$from -> $to: unknown flag '$flag'")
        }
        if (newScope != null) {
          check(scope == null) { "$from -> $to has two scopes" }
          scope = newScope
        }
      }
      return Dependency(from, to, scope ?: JpsJavaDependencyScope.COMPILE, exported)
    }

    private fun valueWithFlags(text: String): Pair<String, Set<String>> {
      val value = text.substringBefore('[').trim()
      if ('[' !in text) return value to emptySet()
      val flags = text.substringAfter('[').substringBefore(']').split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
      return value to flags
    }
  }
}
