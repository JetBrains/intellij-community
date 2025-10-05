// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveModel
import org.jetbrains.kotlin.idea.refactoring.move.withConfiguredRuntime
import org.jetbrains.kotlin.idea.refactoring.rename.loadTestConfiguration
import org.jetbrains.kotlin.idea.refactoring.runRefactoringTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinMultiFileTestCase
import java.io.File

abstract class AbstractK2CheckDescriptorMultiModuleMoveTest : KotlinMultiFileTestCase() {
    override fun getTestRoot(): String = "refactoring/moveDescriptors/"
    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR

    init {
        isMultiModule = true
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun doTest(path: String, configureMoveModel: (K2MoveModel) -> Unit, checkMoveDescriptor: (K2MoveOperationDescriptor<*>) -> Unit) {
        val testDataPath = testDataPath + testRoot + path
        val config = loadTestConfiguration(File(testDataPath))
        doTestCommittingDocuments { rootDir, _ ->
            withConfiguredRuntime(config) {
                runRefactoringTest(testDataPath, config, rootDir, project, object : KotlinMoveRefactoringAction {
                    override fun runRefactoring(
                        rootDir: VirtualFile,
                        mainFile: PsiFile,
                        elementsAtCaret: List<PsiElement>,
                        config: JsonObject
                    ) {
                        allowAnalysisOnEdt {
                            val elementAtCaret = elementsAtCaret.single()
                            val editor = elementAtCaret.findExistingEditor()
                            val moveModel = K2MoveModel.create(elementsAtCaret.toTypedArray<PsiElement>(), null, editor)
                                ?: error("Failed to create move model")
                            configureMoveModel(moveModel)
                            val descriptor = moveModel.toDescriptor()
                            checkMoveDescriptor(descriptor)
                            descriptor.refactoringProcessor().run()
                        }
                    }
                })
            }
        }
    }

    protected fun setMoveModelSetting(setting: K2MoveModel.Setting, newValue: Boolean) {
        val oldSettingValue = setting.state
        setting.state = newValue
        testRootDisposable.whenDisposed {
            setting.state = oldSettingValue
        }
    }
}
