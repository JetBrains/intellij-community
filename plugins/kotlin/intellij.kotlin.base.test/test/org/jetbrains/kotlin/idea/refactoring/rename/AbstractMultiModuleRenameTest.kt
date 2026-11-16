// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.refactoring.loadTestConfiguration
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinMultiFileTestCase
import java.io.File

abstract class AbstractMultiModuleRenameTest : KotlinMultiFileTestCase() {

    override fun getTestRoot(): String = "/renameMultiModule/"
    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR

    open fun doTest(path: String) {
        val renameParamsObject = loadTestConfiguration(File(path))

        val file = renameParamsObject.getString("file")
        val newName = renameParamsObject.getString("newName")
        val isEnabled = renameParamsObject.get("enabledInK2")?.asBoolean != false

        isMultiModule = true

        val results = runCatching {
            doTestCommittingDocuments { rootDir, _ ->
                val mainFile = rootDir.findFileByRelativePath(file)!!
                val psiFile = PsiManager.getInstance(project).findFile(mainFile)!!

                val renameType = renameParamsObject.getString("type")

                when (RenameType.valueOf(renameType)) {
                    RenameType.FILE -> runRename(project, newName, psiFile, renameParamsObject, true, true)
                    RenameType.MARKED_ELEMENT -> doRenameMarkedElement(renameParamsObject, psiFile, ::runRename)
                    else -> fail("Unexpected rename type: $renameType")
                }
            }
        }

        results.fold(
            onSuccess = { require(isEnabled) { "This test passes and should be enabled!" } },
            onFailure = { exception -> if (isEnabled) throw exception }
        )
    }

    /** Runs the rename of [substitution], with every automatic renamer; a subclass renames with another engine. */
    protected open fun runRename(
        project: Project,
        newName: String,
        substitution: PsiElement?,
        renameParamsObject: JsonObject,
        isSearchInComments: Boolean,
        isSearchTextOccurrences: Boolean
    ) {
        runRenameProcessor(project, newName, substitution, renameParamsObject, isSearchInComments, isSearchTextOccurrences)
    }
}