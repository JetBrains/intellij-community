// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.childrenOfType
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.KaClassifierBodyRenderer
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Paths

abstract class AbstractK2KotlinInMarkdownAnalysisTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun runInDispatchThread(): Boolean = false

    @OptIn(KaExperimentalApi::class)
    fun doTest(testdataPath: String) {
        val testDataFile = Paths.get(testdataPath).toFile()
        require(testDataFile.extension == "md") { "Only markdown files are supported" }
        myFixture.configureByFile(testDataFile.name)

        val rendered = runReadAction {
            InjectionTestFixture(myFixture).getAllInjections().mapNotNull { (host, injected) ->
                if (injected !is KtFile) return@mapNotNull null
                assertInstanceOf(injected, KtBlockCodeFragment::class.java)
                buildString {
                    analyze(injected) {
                        appendLine("// INJECTED AT: ${host.textRange}")
                        val block = (injected as KtBlockCodeFragment).children.single() as KtBlockExpression
                        block.childrenOfType<KtDeclaration>().forEach { declaration ->
                            appendLine(
                                declaration.symbol.render(KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
                                    classifierBodyRenderer = KaClassifierBodyRenderer.BODY_WITH_MEMBERS
                                })
                            )
                        }
                    }
                }
            }.sorted().joinToString("\n\n")
        }
        KotlinTestUtils.assertEqualsToFile(testDataFile.resolveSibling(testDataFile.name + ".analyzed"), rendered)
    }
}