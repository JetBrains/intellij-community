// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.tools

import com.intellij.codeInsight.hints.declarative.HintFontSize
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.HintMarginPadding
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.PyBundle
import com.jetbrains.python.inspections.dependencies.DependenciesPsiProviderData
import com.jetbrains.python.inspections.dependencies.ResolvedPsiFile
import com.jetbrains.python.inspections.dependencies.resolvePsiFile
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.requirements.getPythonSdk

/**
 * Renders a small gray "✓ <version>" inlay after each requirement whose package is installed in
 * the active interpreter, with a hover tooltip spelling out the version. Replaces a previous
 * background-color annotator: the hint conveys the same "this is installed" signal AND the
 * version, without tinting the surrounding text or competing with syntax highlighting.
 *
 * Built on the declarative inlay API on purpose: the legacy [InlayHintsProvider] is marked as
 * "very likely to be deprecated" in its own KDoc and the platform pushes new providers toward the
 * declarative API. The trade-off is that [com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder] does not yet expose
 * `icon(...)`, so the "installed" marker is a Unicode glyph instead of a real `AllIcons.*`
 * rendering.
 *
 * Registered for all languages defined by [com.jetbrains.python.inspections.dependencies.DependenciesPsiProvider] extensions.
 */
class DependenciesInlayHintsProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
    val packageManager =
      getPythonSdk(file)
        ?.let { PythonPackageManager.forSdk(file.project, it) }

    if (packageManager == null || !packageManager.tracksDependencyFile(file)) {
      return null
    }

    val installedVersions: Map<PyPackageName, @NlsSafe String> =
      packageManager
        .listInstalledPackagesSnapshot()
        .associate { PyPackageName.from(it.name) to it.version }

    if (installedVersions.isEmpty()) {
      return null
    }

    return Collector(
      installedVersions,
      InjectedLanguageManager.getInstance(file.project)
    )
  }

  private class Collector(
    private val installedVersions: Map<PyPackageName, @NlsSafe String>,
    private val injectedLanguageManager: InjectedLanguageManager,
  ) : SharedBypassCollector {
    private val hintFormat: HintFormat = HintFormat.default
      .withFontSize(HintFontSize.ABitSmallerThanInEditor)
      .withHorizontalMargin(HintMarginPadding.MarginAndSmallerPadding)

    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      val resolvedFile = resolvePsiFile(injectedLanguageManager, element)
      val file = when (resolvedFile) {
        is ResolvedPsiFile.File -> resolvedFile.file
        is ResolvedPsiFile.InjectedFile -> resolvedFile.file
        ResolvedPsiFile.NonFile -> return
      }
      val dependencies =
        DependenciesPsiProviderData
          .dependenciesForFile(file)
          ?.values
          ?.flatMap { it.entries }
        ?: return

      for ((pyRequirement, psiElement) in dependencies) {
        val host = if (resolvedFile.isInjected) element else psiElement
        val hostEndOffset = host.textRange.endOffset

        emitHint(pyRequirement, hostEndOffset, sink)
      }
    }

    private fun emitHint(requirement: PyRequirement, hostEndOffset: Int, sink: InlayTreeSink) {
      val installedVersion = installedVersions[PyPackageName.from(requirement.name)] ?: return
      val hintText = PyBundle.message("INLAY.requirements.installed.version", installedVersion)
      val tooltip = PyBundle.message("INLAY.requirements.installed.tooltip", installedVersion)

      sink.addPresentation(
        position = InlineInlayPosition(offset = hostEndOffset, relatedToPrevious = true),
        tooltip = tooltip,
        hintFormat = hintFormat,
      ) {
        text(hintText)
      }
    }
  }
}
