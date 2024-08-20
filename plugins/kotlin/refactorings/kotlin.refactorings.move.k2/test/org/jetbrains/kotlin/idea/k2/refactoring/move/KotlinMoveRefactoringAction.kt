// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.refactoring.AbstractMultifileRefactoringTest
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings

interface KotlinMoveRefactoringAction : AbstractMultifileRefactoringTest.RefactoringAction {
    fun JsonObject.updateTextOccurrences() = get("searchInNonCode")?.asBoolean?.equals(true)
        ?: KotlinCommonRefactoringSettings.getInstance().UPDATE_TEXT_OCCURENCES

    fun JsonObject.updateUsages() = get("updateUsages")?.asBoolean?.equals(true)
        ?: KotlinCommonRefactoringSettings.getInstance().MOVE_UPDATE_USAGES

    fun JsonObject.moveExpectedActuals() = get("moveExpectedActuals")?.asBoolean?.equals(true)
        ?: KotlinCommonRefactoringSettings.getInstance().MOVE_MPP_DECLARATIONS

    fun shouldUpdateReferences(config: JsonObject, source: PsiElement, target: PsiElement): Boolean {
        fun PsiElement.virtualFile() = if (this is PsiDirectory) virtualFile else containingFile.virtualFile
        val fileIndex = ProjectFileIndex.getInstance(source.project)
        if (!fileIndex.isInSource(source.virtualFile()) || !fileIndex.isInSource(target.virtualFile())) return false
        return config.updateUsages()
    }
}