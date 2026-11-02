// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.jps.model.kotlinCompilerArguments
import org.jetbrains.kotlin.jps.targets.KotlinModuleBuildTarget

/** A chunk of cyclically dependent [KotlinModuleBuildTarget]s. */
class KotlinChunk internal constructor(val context: KotlinCompileContext, val targets: List<KotlinModuleBuildTarget>) {
  val containsTests: Boolean = targets.any { it.isTests }

  val representativeTarget: KotlinModuleBuildTarget
    get() = targets.first()

  val presentableModulesToCompilersList: String
    get() = targets.joinToString { "${it.module.name} (${it.javaClass.simpleName})" }

  val haveSameCompiler: Boolean = targets.all { it.javaClass == representativeTarget.javaClass }

  val compilerArguments: CommonCompilerArguments by lazy {
    representativeTarget.module.kotlinCompilerArguments
  }

  val isEnabled: Boolean by lazy {
    representativeTarget.isEnabled(lazy { compilerArguments })
  }

  /** The same as [org.jetbrains.jps.ModuleChunk.getPresentableShortName]. */
  val presentableShortName: String
    get() = buildString {
      if (containsTests) append("tests of ")
      append(targets.first().module.name)
      if (targets.size > 1) {
        val andXMore = " and ${targets.size - 1} more"
        val other = ", " + targets.asSequence().drop(1).joinToString()
        append(if (other.length < andXMore.length) other else andXMore)
      }
    }

  override fun toString(): String =
    "KotlinChunk<${representativeTarget.javaClass.simpleName}>(${targets.joinToString { it.jpsModuleBuildTarget.presentableName }})"
}
