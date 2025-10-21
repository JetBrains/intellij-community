// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.refactoring.AbstractMultifileRefactoringTest
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings

interface KotlinMoveRefactoringAction : AbstractMultifileRefactoringTest.RefactoringAction {
    fun JsonObject.searchInComments() = get("searchInCommentsAndStrings")?.asBoolean?.equals(true)
        ?: KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_IN_COMMENTS

    fun JsonObject.searchForText() = get("searchInNonCode")?.asBoolean?.equals(true)
        ?: KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_FOR_TEXT

    fun JsonObject.searchReferences() = get("searchReferences")?.asBoolean?.equals(true)
        ?: KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_REFERENCES

    fun JsonObject.moveExpectedActuals() = get("moveExpectedActuals")?.asBoolean?.equals(true)
        ?: KotlinCommonRefactoringSettings.getInstance().MOVE_MPP_DECLARATIONS

    fun JsonObject.moveExplicitPackage() = get("explicitPackage")?.asBoolean == true

    fun shouldUpdateReferences(config: JsonObject, source: PsiElement, target: PsiElement): Boolean {
        fun PsiElement.virtualFile() = if (this is PsiDirectory) virtualFile else containingFile.virtualFile
        val fileIndex = ProjectFileIndex.getInstance(source.project)
        if (!fileIndex.isInSource(source.virtualFile()) || !fileIndex.isInSource(target.virtualFile())) return false
        return config.searchReferences()
    }
}