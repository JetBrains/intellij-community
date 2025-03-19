// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.k2.refactoring.introduceParameter.KotlinFirIntroduceLambdaParameterHandler
import org.jetbrains.kotlin.idea.k2.refactoring.introduceParameter.KotlinFirIntroduceParameterHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractExtractionTest
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.IntroduceParameterDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.KotlinIntroduceParameterHelper
import org.jetbrains.kotlin.idea.refactoring.selectElement
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.util.findElementByCommentPrefix
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration

abstract class AbstractK2IntroduceParameterTest : AbstractExtractionTest() {

    protected fun doIntroduceParameterTest(path: String) {
        doIntroduceParameterTest(path, false)
    }

    protected fun doIntroduceFunctionalParameterTest(path: String) {
        doIntroduceParameterTest(path, true)
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    protected fun doIntroduceParameterTest(unused: String, asLambda: Boolean) {
        doTestIfNotDisabledByFileDirective { file ->
            val fileText = file.text

            open class TestIntroduceParameterHelperImpl : KotlinIntroduceParameterHelper<KtNamedDeclaration> {
                override fun configure(descriptor: IntroduceParameterDescriptor<KtNamedDeclaration>): IntroduceParameterDescriptor<KtNamedDeclaration> = with(descriptor) {
                    val singleReplace = InTextDirectivesUtils.isDirectiveDefined(fileText, "// SINGLE_REPLACE")
                    val withDefaultValue = InTextDirectivesUtils.getPrefixedBoolean(fileText, "// WITH_DEFAULT_VALUE:") ?: true

                    copy(
                        occurrencesToReplace = if (singleReplace) listOf(originalOccurrence) else occurrencesToReplace,
                        withDefaultValue = withDefaultValue
                    )
                }
            }

            val helper = TestIntroduceParameterHelperImpl()
            val handler = if (asLambda) KotlinFirIntroduceLambdaParameterHandler(helper) else KotlinFirIntroduceParameterHandler(helper)
            with(handler) {
                val target = (file as KtFile).findElementByCommentPrefix("// TARGET:") as? KtNamedDeclaration
                if (target != null) {
                    selectElement(fixture.editor, file, false, listOf(ElementKind.EXPRESSION)) { element ->
                        allowAnalysisOnEdt {
                            invoke(fixture.project, fixture.editor, element as KtExpression, target)
                        }
                    }
                } else {
                    invoke(fixture.project, fixture.editor, file, null)
                }
            }
        }
    }


    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}