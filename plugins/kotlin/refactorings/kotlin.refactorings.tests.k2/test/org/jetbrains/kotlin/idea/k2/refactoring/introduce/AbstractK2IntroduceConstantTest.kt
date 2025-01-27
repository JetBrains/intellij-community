// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionResult
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.ExtractionEngineHelper
import org.jetbrains.kotlin.idea.k2.refactoring.introduceConstant.INTRODUCE_CONSTANT
import org.jetbrains.kotlin.idea.k2.refactoring.introduceConstant.KotlinIntroduceConstantHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractExtractionTest
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.propertyTargets
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK2IntroduceConstantTest : AbstractExtractionTest() {

    override fun doIntroduceConstantTest(unused: String) {
        doTestIfNotDisabledByFileDirective { file ->
            file as KtFile

            val extractionTarget = propertyTargets.single {
                it.targetName == InTextDirectivesUtils.findStringWithPrefixes(file.getText(), "// EXTRACTION_TARGET: ")
            }

            val replaceAll = !InTextDirectivesUtils.isDirectiveDefined(file.getText(), "// NO_DUPLICATES")

            val helper = object : ExtractionEngineHelper(INTRODUCE_CONSTANT) {
                override fun configureAndRun(
                    project: Project,
                    editor: Editor,
                    descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
                    onFinish: (ExtractionResult) -> Unit
                ) {
                    doRefactor(
                        ExtractionGeneratorConfiguration(
                            descriptorWithConflicts.descriptor,
                            ExtractionGeneratorOptions(target = extractionTarget, delayInitialOccurrenceReplacement = true, isConst = true)
                        ),
                        onFinish
                    )
                }

                override fun replaceAllByDefault(): Boolean {
                    return replaceAll
                }
            }
            val handler = KotlinIntroduceConstantHandler(helper)
            val editor = fixture.editor
            handler.selectElements(editor, file) { elements, target ->
                handler.doInvoke(project, editor, file, elements, target)
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