// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.childrenOfType
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOutput
import org.jetbrains.kotlin.analysis.api.rendering.render
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.util.withShortNames
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScript
import java.nio.file.Paths

abstract class AbstractK2KotlinInMarkdownAnalysisTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun runInDispatchThread(): Boolean = false

    fun doTest(testdataPath: String) {
        val testDataFile = Paths.get(testdataPath).toFile()
        require(testDataFile.extension == "md") { "Only markdown files are supported" }
        myFixture.configureByFile(testDataFile.name)

        val rendered = runReadAction {
            InjectionTestFixture(myFixture).getAllInjections().mapNotNull { (host, injected) ->
                if (injected !is KtFile) return@mapNotNull null
                assertInstanceOf(injected, KtFile::class.java)
                buildString {
                    analyze(injected) {
                        appendLine("// INJECTED AT: ${host.textRange}")
                        val block = injected.children.filterIsInstance<KtScript>().single().children.single()
                        block.childrenOfType<KtDeclaration>().forEach { declaration ->
                            val output = KaRenderingOutput.plainString(indentationUnit = "  ")
                            KaRenderer.withShortNames.render(declaration.symbol, output)
                            appendLine(output.toString())
                        }
                    }
                }
            }.sorted().joinToString("\n\n")
        }
        KotlinTestUtils.assertEqualsToFile(testDataFile.resolveSibling(testDataFile.name + ".analyzed"), rendered)
    }
}