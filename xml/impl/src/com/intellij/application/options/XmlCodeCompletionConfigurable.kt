package com.intellij.application.options

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.xml.XmlBundle
import com.intellij.xml.XmlCoreBundle

class XmlCodeCompletionConfigurable : UiDslUnnamedConfigurable.Simple(), Configurable {

  override fun getDisplayName(): String {
    return XmlCoreBundle.message("options.html.display.name")
  }

  override fun Panel.createContent() {
    val htmlSettings = HtmlSettings.getInstance()

    group(XmlCoreBundle.message("options.html.display.name")) {
      row {
        checkBox(XmlBundle.message("checkbox.enable.completion.html.auto.popup.code.completion.on.typing.in.text"))
          .bindSelected({ htmlSettings.AUTO_POPUP_TAG_CODE_COMPLETION_ON_TYPING_IN_TEXT }, { htmlSettings.AUTO_POPUP_TAG_CODE_COMPLETION_ON_TYPING_IN_TEXT = it })
      }
    }
  }
}
