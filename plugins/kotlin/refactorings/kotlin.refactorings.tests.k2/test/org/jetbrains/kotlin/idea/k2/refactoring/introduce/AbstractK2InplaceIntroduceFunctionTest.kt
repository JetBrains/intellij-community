// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import com.intellij.application.options.CodeStyle
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.LightProjectDescriptor
import org.editorconfig.Utils
import org.editorconfig.configmanagement.EditorConfigActionUtil
import org.editorconfig.configmanagement.extended.EditorConfigCodeStyleSettingsModifier
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.KotlinFirExtractFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractInplaceIntroduceFunctionTest
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.AbstractExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractK2InplaceIntroduceFunctionTest : AbstractInplaceIntroduceFunctionTest() {

    override fun getExtractFunctionHandler(allContainersEnabled: Boolean): AbstractExtractKotlinFunctionHandler =
        KotlinFirExtractFunctionHandler(allContainersEnabled)

    override fun tearDown() {
        runAll(
          { myFixture.project.invalidateCaches() },
          { super.tearDown() },
        )
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun doTestWithEditorConfig() {
        val temporarySettings = CodeStyleSettingsManager.getInstance(project).temporarySettings
        CodeStyle.dropTemporarySettings(project)
        val editorConfigEnabled = Utils.isEnabled(project)
        try {
            EditorConfigCodeStyleSettingsModifier.Handler.setEnabledInTests(true)
            Utils.isEnabledInTests = true
            Utils.setFullIntellijSettingsSupportEnabledInTest(true)
            EditorConfigActionUtil.setEditorConfigEnabled(project, true)
            myFixture.addFileToProject(
                ".editorconfig", """
                root = true

                [*.kt]
                ij_kotlin_code_style_defaults = KOTLIN_OFFICIAL
                indent_style = space
                indent_size = 2
                ij_continuation_indent_size = 2
                max_line_length = 80
                ij_kotlin_allow_trailing_comma = true
                ij_kotlin_continuation_indent_in_parameter_lists = true
                ij_kotlin_else_on_new_line = true
            """.trimIndent()
            )
            Utils.fireEditorConfigChanged(project)
            doTestWithConfiguredStyle()
            assertEquals(2, CodeStyle.getIndentOptions(file).INDENT_SIZE)
        } finally {
            EditorConfigActionUtil.setEditorConfigEnabled(project, editorConfigEnabled)
            Utils.isEnabledInTests = false
            Utils.setFullIntellijSettingsSupportEnabledInTest(false)
            EditorConfigCodeStyleSettingsModifier.Handler.setEnabledInTests(false)
            Utils.fireEditorConfigChanged(project)
            if (temporarySettings != null) {
                CodeStyle.setTemporarySettings(project, temporarySettings)
            }
        }
    }
}