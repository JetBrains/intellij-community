// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.*
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import com.intellij.xml.XmlBundle
import java.util.function.Function

// @formatter:off
private val model = WebEditorOptions.getInstance()
private val myAutomaticallyInsertClosingTagCheckBox                     get() = CheckboxDescriptor(XmlBundle.message("smart.keys.insert.closing.tag.on.tag.completion"), PropertyBinding(model::isAutomaticallyInsertClosingTag, model::setAutomaticallyInsertClosingTag))
private val myAutomaticallyInsertRequiredAttributesCheckBox             get() = CheckboxDescriptor(XmlBundle.message("smart.keys.insert.required.attributes.on.tag.completion"), PropertyBinding(model::isAutomaticallyInsertRequiredAttributes, model::setAutomaticallyInsertRequiredAttributes))
private val myAutomaticallyInsertRequiredSubTagsCheckBox                get() = CheckboxDescriptor(XmlBundle.message("smart.keys.insert.required.subtags.on.tag.completion"), PropertyBinding(model::isAutomaticallyInsertRequiredSubTags, model::setAutomaticallyInsertRequiredSubTags))
private val myAutomaticallyStartAttributeAfterCheckBox                  get() = CheckboxDescriptor(XmlBundle.message("smart.keys.start.attribute.on.tag.completion"), PropertyBinding(model::isAutomaticallyStartAttribute, model::setAutomaticallyStartAttribute))
private val myAddQuotasForAttributeValue                                get() = CheckboxDescriptor(XmlBundle.message("smart.keys.add.quotes.for.attribute.value.on.typing.equal.and.attribute.completion"), PropertyBinding(model::isInsertQuotesForAttributeValue, model::setInsertQuotesForAttributeValue))
private val myAutoCloseTagCheckBox                                      get() = CheckboxDescriptor(XmlBundle.message("smart.keys.auto.close.tag.on.typing.less"), PropertyBinding(model::isAutoCloseTag, model::setAutoCloseTag))
private val mySyncTagEditing                                            get() = CheckboxDescriptor(XmlBundle.message("smart.keys.simultaneous.tags.editing"), PropertyBinding(model::isSyncTagEditing, model::setSyncTagEditing))
private val mySelectWholeCssIdentifierOnDoubleClick                     get() = CheckboxDescriptor(XmlBundle.message("smart.keys.select.whole.css.identifiers.on.double.click"), PropertyBinding(model::isSelectWholeCssIdentifierOnDoubleClick, model::setSelectWholeCssIdentifierOnDoubleClick))
// @formatter:on

private val webEditorOptionDescriptors
  get() = listOf(
    myAutomaticallyInsertClosingTagCheckBox,
    myAutomaticallyInsertRequiredAttributesCheckBox,
    myAutomaticallyInsertRequiredSubTagsCheckBox,
    myAutomaticallyStartAttributeAfterCheckBox,
    myAddQuotasForAttributeValue,
    myAutoCloseTagCheckBox,
    mySyncTagEditing,
    mySelectWholeCssIdentifierOnDoubleClick
  ).map(CheckboxDescriptor::asOptionDescriptor)

internal class WebSmartKeysConfigurable : BoundCompositeConfigurable<UnnamedConfigurable>(
  XmlBundle.message("configurable.name.html.css")), ConfigurableWithOptionDescriptors, Configurable.WithEpDependencies {
  override fun createPanel(): DialogPanel {
    return panel {
      group(XmlBundle.message("xml.editor.options.misc.title")) {
        row {
          checkBox(myAutomaticallyInsertClosingTagCheckBox)
        }
        row {
          checkBox(myAutomaticallyInsertRequiredAttributesCheckBox)
        }
        row {
          checkBox(myAutomaticallyInsertRequiredSubTagsCheckBox)
        }
        row {
          checkBox(myAutomaticallyStartAttributeAfterCheckBox)
        }
        row {
          checkBox(myAddQuotasForAttributeValue)
        }
        row {
          checkBox(myAutoCloseTagCheckBox)
        }
        row {
          checkBox(mySyncTagEditing)
        }
      }
      group(XmlBundle.message("xml.editor.options.css.title")) {
        row {
          checkBox(mySelectWholeCssIdentifierOnDoubleClick)
        }
      }
      for (configurable in configurables) {
        appendDslConfigurable(configurable)
      }
    }
  }

  override fun getOptionDescriptors(configurableId: String, nameConverter: Function<in String, String>) = webEditorOptionDescriptors

  override fun createConfigurables(): List<UnnamedConfigurable> {
    return ConfigurableWrapper.createConfigurables(EP_NAME)
  }

  companion object {
    val EP_NAME = ExtensionPointName.create<WebSmartKeysConfigurableEP>("com.intellij.webSmartKeysConfigurable")
  }

  override fun getDependencies(): Collection<BaseExtensionPointName<*>> {
    return listOf(EP_NAME)
  }
}

internal class WebSmartKeysConfigurableEP : ConfigurableEP<UnnamedConfigurable>()
