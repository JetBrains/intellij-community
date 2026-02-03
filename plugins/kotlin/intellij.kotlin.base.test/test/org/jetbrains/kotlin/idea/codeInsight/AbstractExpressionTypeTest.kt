// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.lang.LanguageExpressionTypes
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.runInEdtAndGet
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinMultiFileLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractExpressionTypeTest : KotlinMultiFileLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun runInDispatchThread(): Boolean = false

    private fun findKotlinExpressionTypeProvider(): KotlinExpressionTypeProvider {
        val providers = LanguageExpressionTypes.INSTANCE
            .allForLanguage(KotlinLanguage.INSTANCE)
            .filterIsInstance<KotlinExpressionTypeProvider>()

        assertSize(1, providers)

        return providers.single()
    }

    private val expectedTypeDirective: String
        get() = when (pluginMode) {
            KotlinPluginMode.K1 -> "// K1_TYPE: "
            KotlinPluginMode.K2 -> "// K2_TYPE: "
        }

    override fun doMultiFileTest(
        files: List<PsiFile>,
        globalDirectives: Directives
    ) {
        val mainFile = files.first() as KtFile

        myFixture.configureFromExistingVirtualFile(mainFile.virtualFile)
        val expressionTypeProvider = findKotlinExpressionTypeProvider()
        val elementAtCaret = runReadAction { myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!! }
        val expressions = runInEdtAndGet { expressionTypeProvider.getExpressionsAt(elementAtCaret) }
        runReadAction {
            val types = expressions.map { "${it.text.replace('\n', ' ')} -> ${expressionTypeProvider.getInformationHint(it)}" }
            val expectedTypes = InTextDirectivesUtils.findLinesWithPrefixesRemoved(myFixture.file.text, expectedTypeDirective)
            UsefulTestCase.assertOrderedEquals(types, expectedTypes)
        }
    }
}