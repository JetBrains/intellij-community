package com.intellij.python.ruff.server

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import com.intellij.python.lsp.core.PyLspService
import com.intellij.python.lsp.core.PyLspToolCustomization
import com.intellij.python.lsp.core.PyLspToolDescriptor
import com.intellij.python.lsp.core.PyLspToolIntegrationProvider
import com.intellij.python.ruff.RuffBundle
import com.intellij.python.ruff.RuffConfiguration
import com.intellij.python.ruff.RuffPyTool
import com.intellij.python.ruff.RuffService
import com.intellij.python.ruff.RuffSettings
import com.intellij.python.ruff.codeinsight.actions.RuffDisableRuleForFileIntentionAction
import com.intellij.python.ruff.codeinsight.actions.RuffDisableRuleIntentionAction
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.InitializeResult
import org.jetbrains.annotations.Nls

class RuffLspIntegrationProvider : PyLspToolIntegrationProvider() {
  override fun getDescriptor(module: Module): RuffLspClientDescriptor =
    RuffLspClientDescriptor(module)
}

class RuffLspClientDescriptor(module: Module) : PyLspToolDescriptor(module, RuffPyTool.getInstance()) {
  override val toolConfig: RuffSettings
    get() = project.service<RuffConfiguration>()

  override fun lspArguments(): List<String> {
    return listOf("server")
  }

  override val lspCustomization: PyLspToolCustomization = object : PyLspToolCustomization(toolConfig, pyTool, project) {
    override val formattingCustomizer = object : LspFormattingSupport() {
      override fun shouldFormatThisFileExclusivelyByServer(
        file: VirtualFile,
        ideCanFormatThisFileItself: Boolean,
        serverExplicitlyWantsToFormatThisFile: Boolean,
      ): Boolean {
        return this@RuffLspClientDescriptor.toolConfig.formatting
      }
    }

    override val diagnosticsSupport: PyLspToolDiagnosticsSupport = object : PyLspToolDiagnosticsSupport() {
      override fun customizeQuickFixes(diagnostic: Diagnostic, quickFixes: List<IntentionAction>): List<IntentionAction> {
        val disableQuickFix = if (quickFixes.count {
            // there are always 8, we need to count the filled ones
            it.text.isNotEmpty()
          } > 1) quickFixes.firstOrNull { ": Disable for this line" in it.text }?.let {
          object : PyLspAction(diagnostic, it) {
            override fun getText(): @IntentionName String {
              return RuffBundle.message("intention.name.disable.for.this.line")
            }
          }
        }
        else null
        return quickFixes.mapNotNull { quickfix ->
          if (quickfix === disableQuickFix?.action) null
          else object : PyLspAction(diagnostic, quickfix) {
            override fun getText(): @IntentionName String {
              return quickFixMessage(quickfix.text)
            }

            override fun getOptions(): List<IntentionAction> =
              quickFixOptions(diagnostic).let {
                if (disableQuickFix != null) {
                  listOf(disableQuickFix) + it
                }
                else it
              }
          }
        }
      }
    }

    override fun codeCustomizer(@Nls code: String): String =
      project.service<RuffService>().ruleInformation[code]?.name ?: code

    override fun quickFixMessage(text: String): @IntentionName String {
      @Suppress("HardCodedStringLiteral")
      return Regex("""(?<=Ruff \()(\w+)""").replace(text) { codeCustomizer(it.groupValues[1]) }
    }

    override fun quickFixOptions(diagnostic: Diagnostic): List<IntentionAction> {
      val code = diagnostic.code?.get()?.toString() ?: return emptyList()
      return listOf(
        RuffDisableRuleIntentionAction(code),
        RuffDisableRuleForFileIntentionAction(code),
      )
    }
  }

  override val commandDescriptions: Map<String, String?> = mapOf(
    "ruff.applyFormat" to "Format document",
    "ruff.applyAutofix" to null,
    "ruff.applyOrganizeImports" to null,
    "ruff.printDebugInformation" to "Print debug information",
  )

  override val lspServerListener: PyLspToolDescriptorLspServerListener = object : PyLspToolDescriptorLspServerListener() {
    override fun serverInitialized(params: InitializeResult) {
      super.serverInitialized(params)
      val service = project.service<RuffService>()
      project.service<PyLspService>().cs.launch(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
        service.gatherRuleInformation()
        service.gatherConfigOptionInformation()
      }
    }
  }
}
