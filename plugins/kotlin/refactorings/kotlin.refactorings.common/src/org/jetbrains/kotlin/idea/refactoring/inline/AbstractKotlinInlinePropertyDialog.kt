// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.psi.PsiReference
import com.intellij.refactoring.HelpID
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.psi.KtProperty
import kotlin.reflect.KMutableProperty1

abstract class AbstractKotlinInlinePropertyDialog(property: KtProperty,
                                                  reference: PsiReference?,
                                                  withPreview: Boolean = true,
                                                  editor: Editor?,) : AbstractKotlinInlineDialog<KtProperty>(property, reference, editor) {

    protected val simpleLocal = declaration.isLocal && (reference == null || occurrencesNumber == 1)

    init {
        setPreviewResults(withPreview && shouldBeShown())
        if (simpleLocal) {
            setDoNotAskOption(object : DoNotAskOption {
                override fun isToBeShown() = EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog

                override fun setToBeShown(value: Boolean, exitCode: Int) {
                    EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog = value
                }

                override fun canBeHidden() = true

                override fun shouldSaveOptionsOnCancel() = false

                override fun getDoNotShowMessage() = KotlinBundle.message("message.do.not.show.for.local.variables.in.future")
            })
        }
        init()
    }

    override fun isKeepTheDeclarationByDefault(): Boolean {
        return !simpleLocal && super.isKeepTheDeclarationByDefault()
    }

    override val inlineThisOption: KMutableProperty1<KotlinCommonRefactoringSettings, Boolean> get() = KotlinCommonRefactoringSettings::INLINE_LOCAL_THIS
    override val inlineKeepOption: KMutableProperty1<KotlinCommonRefactoringSettings, Boolean> get() = KotlinCommonRefactoringSettings::INLINE_PROPERTY_KEEP
    fun shouldBeShown() = !simpleLocal || reference != null && EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog
    override fun doHelpAction() = HelpManager.getInstance().invokeHelp(HelpID.INLINE_VARIABLE)
    abstract override fun createProcessor(): AbstractKotlinInlinePropertyProcessor
}