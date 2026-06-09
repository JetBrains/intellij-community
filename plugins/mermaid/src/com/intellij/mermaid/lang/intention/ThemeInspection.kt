// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.intention

import com.intellij.codeInspection.InspectionManagerBase
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.parser.ParserUtils
import com.intellij.mermaid.lang.psi.MermaidDirective
import com.intellij.mermaid.lang.psi.MermaidElementFactory
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.mermaid.lang.psi.MermaidVisitor
import com.intellij.mermaid.lang.psi.children
import com.intellij.mermaid.lang.psi.hasType
import com.intellij.mermaid.settings.MermaidSettings
import com.intellij.mermaid.settings.MermaidSettingsState
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.parents
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.ui.ColorUtil

class ThemeInspection : LocalInspectionTool() {

  private val notDarkMermaidThemes = setOf(
    MermaidSettingsState.Theme.DEFAULT.value,
    MermaidSettingsState.Theme.NEUTRAL.value,
    MermaidSettingsState.Theme.FOREST.value,
    MermaidSettingsState.Theme.BASE.value
  )

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (holder.file !is MermaidFile) return PsiElementVisitor.EMPTY_VISITOR
    return object : MermaidVisitor() {
      override fun visitDirective(directive: MermaidDirective) {
        val directiveValue = directive.children().firstOrNull { it.hasType(ParserUtils.DIRECTIVE_VALUE) } ?: return

        val scheme = EditorColorsManager.getInstance().globalScheme
        if (!ColorUtil.isDark(scheme.defaultBackground)) return

        val injectedLanguageManager = InjectedLanguageManager.getInstance(directiveValue.project)
        val injectedElement =
          injectedLanguageManager.findInjectedElementAt(directiveValue.containingFile, directiveValue.startOffset) ?: return
        val directiveObject =
          injectedElement.parents(withSelf = true).filterIsInstance<JsonObject>().firstOrNull() ?: return
        val theme = findThemeValue(directiveObject)

        when (theme?.value) {
          null -> {
            val mermaidSettings = MermaidSettings.getInstance()
            val settingsTheme = mermaidSettings.theme.value
            if (settingsTheme != "dark") {
              // TODO: notify if theme in Mermaid settings is not dark when editor theme is dark
            }
          }

          in notDarkMermaidThemes -> {
            holder.registerProblem(
              InspectionManagerBase.getInstance(holder.project)
                .createProblemDescriptor(
                  directiveValue,
                  TextRange.create(theme.startOffset + 1, theme.endOffset - 1),
                  MermaidBundle.message("theme.inspection.non.dark.theme.display.name"),
                  ProblemHighlightType.WARNING,
                  isOnTheFly,
                  ReplaceThemeQuickFix()
                )
            )
          }
        }
      }

      private fun findThemeValue(directive: JsonObject): JsonStringLiteral? {
        return SyntaxTraverser.psiTraverser(directive)
          .asSequence()
          .filterIsInstance<JsonProperty>()
          .filter { it.name == "theme" }
          .map { it.value }
          .filterIsInstance<JsonStringLiteral>()
          .lastOrNull()
      }
    }
  }

  class ReplaceThemeQuickFix : LocalQuickFix {
    override fun getFamilyName() = MermaidBundle.message("fix.replace.theme.directive")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val directiveValue = descriptor.psiElement
      val textRange = descriptor.textRangeInElement

      val newDirectiveText = directiveValue.text.replaceRange(textRange.startOffset, textRange.endOffset, "dark")
      val newDirectiveValue = MermaidElementFactory.createDirectiveValue(project, newDirectiveText)
      checkNotNull(newDirectiveValue)

      directiveValue.replace(newDirectiveValue)
    }
  }
}
