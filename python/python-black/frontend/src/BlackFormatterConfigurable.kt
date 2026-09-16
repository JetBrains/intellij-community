// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.black.frontend

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.project.projectId
import com.intellij.python.black.common.PyBlackToolConfigurationDto
import com.intellij.python.pytools.common.PyToolActionSource
import com.intellij.python.pytools.common.PyToolApi
import com.intellij.python.pytools.common.PyToolEventKind
import com.intellij.python.pytools.common.PyToolDescriptorDto
import com.intellij.python.pytools.common.PyToolLogEventRequest
import com.intellij.python.pytools.common.PyToolRequest
import com.intellij.python.pytools.common.PyToolSetConfigurationRequest
import com.intellij.python.pytools.common.getConfiguration
import com.intellij.python.black.frontend.PyBlackFrontendBundle.message
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.UIUtil

internal class BlackFormatterConfigurable(
  private val project: Project,
  private val descriptor: PyToolDescriptorDto,
) : BoundConfigurable(message("black.configurable.name")) {
  private val request = PyToolRequest(project.projectId(), BLACK_TOOL_ID)
  private val configuration = runWithModalProgressBlocking(project, message("black.configurable.name")) {
    PyToolApi.getInstance().getConfiguration<PyBlackToolConfigurationDto>(request)
  }
  private var storedArguments = configuration.arguments

  private val cliArgumentsTextField = BlackTextFieldWithAutoCompletion(project, object :
    TextFieldWithAutoCompletionListProvider<BlackCliOptionFlag>(BLACK_OPTIONS.toCliOptionFlags()) {

    override fun getLookupString(item: BlackCliOptionFlag): String = item.flag + " "

    override fun getTailText(item: BlackCliOptionFlag): String? = item.option.param

    override fun getTypeText(item: BlackCliOptionFlag): String = item.description()
  })

  override fun createPanel(): DialogPanel = panel {
    row {
      descriptor.minimumSupportedVersion?.let {
        comment(message("black.minimum.supported.version.hint", it))
      }
    }
    row(message("black.cli.args.text.field.label")) {
      cell(cliArgumentsTextField)
        .resizableColumn()
        .align(AlignX.FILL)
        .applyToComponent { background = UIUtil.getTextFieldBackground() }
        .onApply { storedArguments = cliArgumentsTextField.text }
        .onReset { cliArgumentsTextField.text = storedArguments }
        .onIsModified { cliArgumentsTextField.text != storedArguments }
        .comment(message("black.cli.args.comment"), MAX_LINE_LENGTH_WORD_WRAP)
    }
  }

  override fun apply() {
    super.apply()
    runWithModalProgressBlocking(project, message("black.configurable.name")) {
      val api = PyToolApi.getInstance()
      api.setConfiguration(PyToolSetConfigurationRequest(request, configuration.copy(arguments = storedArguments)))
      api.logEvent(PyToolLogEventRequest(request, PyToolActionSource.SETTINGS_DETAIL, PyToolEventKind.CONFIGURATION_CHANGED))
    }
  }

  private class BlackTextFieldWithAutoCompletion(
    project: Project,
    provider: TextFieldWithAutoCompletionListProvider<BlackCliOptionFlag>,
  ) : TextFieldWithCompletion(project, provider, "", true, true, false) {
    override fun getText(): String = super.getText().trimEnd()
  }
}

private class BlackCliOptionFlag(val flag: String, val option: BlackFormatterOption) {
  fun description(): String {
    if (isPrimaryFlag(flag)) return option.description
    val primaryFlag = option.flags.find(::isPrimaryFlag)
    return if (primaryFlag != null) "See $primaryFlag" else option.description
  }
}

private fun isPrimaryFlag(flag: String): Boolean = flag.startsWith("--")

private data class BlackFormatterOption(val flags: List<String>, val param: String?, val description: String)

private fun List<BlackFormatterOption>.toCliOptionFlags(): List<BlackCliOptionFlag> = flatMap { option ->
  option.flags.map { BlackCliOptionFlag(it, option) }
}

private val BLACK_OPTIONS: List<BlackFormatterOption> = listOf(
  BlackFormatterOption(listOf("-l", "--line-length"), "<length>", "How many characters per line to allow. [default: 88]"),
  BlackFormatterOption(listOf("-x", "--skip-source-first-line"), null, "Skip the first line of the source code"),
  BlackFormatterOption(listOf("-S", "--skip-string-normalization"), null, "Don't normalize string quotes or prefixes"),
  BlackFormatterOption(listOf("-C", "--skip-magic-trailing-comma"), null, "Don't use trailing commas as a reason to split lines"),
  BlackFormatterOption(listOf("--fast", "--safe"), null, "Skip temporary sanity checks [default: --safe]"),
  BlackFormatterOption(listOf("--config"), "FILE", "Read configuration from FILE path."),
  BlackFormatterOption(
    listOf("--preview"),
    null,
    """
      Enable potentially disruptive style changes
      that may be added to Black's main
      functionality in the next major release.
    """.trimIndent(),
  ),
  BlackFormatterOption(
    listOf("-t", "--target-version"),
    "[ver1, ver2..]",
    """
      Python versions that should be supported by
      Black's output. By default, Black will try to
      infer this from the project metadata in
      pyproject.toml. If this does not yield
      conclusive results, Black will use per-file
      auto-detection.
    """.trimIndent(),
  ),
)
