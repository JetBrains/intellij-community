// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.editor

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.openapi.options.BeanConfigurable
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle.message

private val editorOptions get() = KotlinEditorOptions.getInstance()

private val cbConvertPastedJavaToKotlin
    get() = CheckboxDescriptor(
        message("editor.checkbox.title.convert.pasted.java.code.to.kotlin"),
        editorOptions::isEnableJavaToKotlinConversion, editorOptions::setEnableJavaToKotlinConversion
    )

private val cbDontShowJavaToKotlinConversionDialog
    get() = CheckboxDescriptor(
        message("editor.checkbox.title.don.t.show.java.to.kotlin.conversion.dialog.on.paste"),
        editorOptions::isDonTShowConversionDialog, editorOptions::setDonTShowConversionDialog
    )

private val cbAutoAddValKeywordToCtorParameters
    get() = CheckboxDescriptor(
        message("editor.checkbox.title.auto.add.val.keyword.to.data.value.class.constructor.parameters"),
        editorOptions::isAutoAddValKeywordToDataClassParameters, editorOptions::setAutoAddValKeywordToDataClassParameters
    )

@NonNls
const val ID = "editor.title.kotlin"

internal val kotlinEditorOptionsDescriptors
    get() = sequenceOf(
        cbConvertPastedJavaToKotlin,
        cbDontShowJavaToKotlinConversionDialog,
        cbAutoAddValKeywordToCtorParameters
    ).map(CheckboxDescriptor::asUiOptionDescriptor)

class KotlinEditorOptionsConfigurable : BeanConfigurable<KotlinEditorOptions>(editorOptions, message(ID)) {

    init {
        checkBox(cbConvertPastedJavaToKotlin)
        checkBox(cbDontShowJavaToKotlinConversionDialog)
        checkBox(cbAutoAddValKeywordToCtorParameters)
    }

    private fun checkBox(checkboxDescriptor: CheckboxDescriptor) {
        checkBox(checkboxDescriptor.name, checkboxDescriptor.getter, checkboxDescriptor.setter)
    }
}