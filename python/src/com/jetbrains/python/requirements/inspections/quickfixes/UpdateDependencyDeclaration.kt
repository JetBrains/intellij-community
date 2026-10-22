// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.quickfixes

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.python.requirements.RequirementsLanguage
import com.intellij.python.requirements.parser.psi.NameReq
import com.intellij.python.requirements.parser.psi.Version
import com.intellij.python.requirements.parser.psi.VersionOne
import org.jetbrains.annotations.ApiStatus

/**
 * Rewrites the declared version of a `requirements.txt` entry after a `pip install --upgrade`, so the file
 * reflects the installed version. Only `requirements.txt`-style entries ([NameReq]) are updated; other
 * build files (`pyproject.toml`, `Pipfile`, …) are out of scope.
 *
 * Must be called under a write command action.
 */
@ApiStatus.Internal
object UpdateDependencyDeclaration {

  /** Operators whose version token is safe to update to the installed version. */
  private val PINNABLE_OPERATORS = setOf("==", "~=")

  fun apply(declaration: PsiElement, newVersion: String) {
    if (declaration !is NameReq) return
    // Pip's `--require-hashes` fails whenever we leave the hash out of sync with the installed artifact.
    // Rather than silently dropping or recomputing the hash, skip the entry so the user notices.
    if (declaration.longOptionList.any { it.text.startsWith("--hash") }) return
    val target = singlePinnableVersion(declaration) ?: return
    val sample = createSampleNameReq(declaration.project, declaration.packageName.text, newVersion) ?: return
    val newVersionPsi = PsiTreeUtil.findChildOfType(sample, Version::class.java) ?: return
    replacePreservingFormatting(target, newVersionPsi)
  }

  /**
   * The sole [Version] token of a [NameReq] that it is safe to swap:
   * - a single [VersionOne] clause whose operator is `==` or `~=`.
   * Returns `null` for every other shape, including compound specifiers (`>=1.0,<2.0`), unpinned entries,
   * and clauses with non-pinnable operators.
   */
  private fun singlePinnableVersion(declaration: NameReq): Version? {
    val versionOnes = declaration.versionspec?.versionOneList ?: return null
    val single = versionOnes.singleOrNull() ?: return null
    if (single.versionCmp.text !in PINNABLE_OPERATORS) return null
    return single.version
  }

  private fun createSampleNameReq(project: Project, packageName: String, newVersion: String): NameReq? {
    val file = PsiFileFactory.getInstance(project)
      .createFileFromText("dummy.requirements", RequirementsLanguage, "$packageName==$newVersion")
    return file.children.firstOrNull { it is NameReq } as? NameReq
  }

  private fun replacePreservingFormatting(target: PsiElement, replacement: PsiElement) {
    PostprocessReformattingAspect.getInstance(target.project).disablePostprocessFormattingInside {
      target.replace(replacement)
    }
  }
}
