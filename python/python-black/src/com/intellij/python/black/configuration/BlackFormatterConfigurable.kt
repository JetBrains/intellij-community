// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.black.configuration

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.UIUtil
import com.intellij.python.black.BlackPyTool
import com.intellij.python.black.PyBlackBundle.message
import com.intellij.python.black.configuration.BlackFormatterConfiguration.BlackFormatterOption.Companion.toCliOptionFlags

class BlackFormatterConfigurable(val project: Project) : BoundConfigurable(message("black.configurable.name")) {
  internal val storedState = BlackFormatterConfiguration.getBlackConfiguration(project)

  private val cliArgumentsTextField = BlackTextFieldWithAutoCompletion(project, object :
    com.intellij.ui.TextFieldWithAutoCompletionListProvider<BlackFormatterConfiguration.CliOptionFlag>(
      BlackFormatterConfiguration.options.toCliOptionFlags()) {

    override fun getLookupString(item: BlackFormatterConfiguration.CliOptionFlag): String = item.flag + " "

    override fun getTailText(item: BlackFormatterConfiguration.CliOptionFlag): String? = item.option.param

    override fun getTypeText(item: BlackFormatterConfiguration.CliOptionFlag): String = item.description()
  })

  override fun createPanel(): DialogPanel = panel {
    row {
      comment(message("black.minimum.supported.version.hint",
                      BlackPyTool.getInstance().minimumSupportedVersion.toCompactString()))
    }
    row(message("black.cli.args.text.field.label")) {
      cell(cliArgumentsTextField)
        .resizableColumn()
        .align(AlignX.FILL)
        .applyToComponent { background = UIUtil.getTextFieldBackground() }
        .onApply { storedState.cmdArguments = cliArgumentsTextField.text }
        .onReset { cliArgumentsTextField.text = storedState.cmdArguments }
        .onIsModified { cliArgumentsTextField.text != storedState.cmdArguments }
        .comment(message("black.cli.args.comment"), MAX_LINE_LENGTH_WORD_WRAP)
    }
  }

  class BlackTextFieldWithAutoCompletion(
    project: Project,
    provider: TextFieldWithAutoCompletionListProvider<BlackFormatterConfiguration.CliOptionFlag>,
  ) : com.intellij.util.textCompletion.TextFieldWithCompletion(project, provider, "", true, true, false) {
    override fun getText(): String = super.getText().trimEnd()
  }
}
