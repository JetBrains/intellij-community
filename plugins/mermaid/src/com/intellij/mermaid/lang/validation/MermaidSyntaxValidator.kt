// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.validation

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * A syntax problem reported by the bundled mermaid itself, rather than by this plugin's grammar.
 *
 * @param message text as mermaid produced it. Terse, and phrased for the renderer's own parser
 *   ("Parse error on line 3: ... Expecting X, got Y"), but authoritative about what mermaid rejects.
 * @param line 1-based line within the diagram text, extracted from [message] when mermaid included one.
 *   Null when mermaid did not say where the problem is, in which case the whole diagram is highlighted.
 */
data class MermaidSyntaxProblem(val message: String, val line: Int?)

/**
 * Delegates "is this actually an error" to the mermaid build we ship, instead of trying to keep our grammar
 * bug-for-bug identical to it.
 *
 * This inverts the plugin's usual failure mode. Our own grammar stays deliberately permissive and owns the
 * things mermaid cannot give us -- PSI, folding, completion, rename, formatting -- so a gap in it costs
 * highlighting fidelity rather than painting valid documents red. What is and is not an error comes from the
 * implementation that actually draws the diagram.
 *
 * Implementations need a JCEF browser, which lives in an optional content module, so this is an extension
 * point rather than a service: with no implementation registered the annotator simply reports nothing.
 */
interface MermaidSyntaxValidator {
  /**
   * Returns problems mermaid found in [text], or an empty list if it parsed cleanly.
   *
   * Returns null when validation could not run at all -- no preview open for [file], browser still loading,
   * unavailable in this environment. Null is deliberately distinct from an empty list: "nothing was checked"
   * must not be reported as "nothing is wrong".
   */
  suspend fun validate(project: Project, file: VirtualFile, text: String): List<MermaidSyntaxProblem>?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<MermaidSyntaxValidator> =
      ExtensionPointName.create("com.intellij.mermaid.syntaxValidator")
  }
}
