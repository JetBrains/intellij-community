// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.psi.PsiFile

data class KotlinConfiguratorChangedFile(
    val file: PsiFile,
    val originalContent: String,
    val modifiedContent: String,
)

class ChangedConfiguratorFiles {
    private val originalFileContents: MutableMap<PsiFile, String> = mutableMapOf()

    /**
     * Stores the contents of the [file] so it can be compared later and checked if it changed.
     * If the file was already stored, it is not stored again.
     */
    fun storeOriginalFileContent(file: PsiFile) {
        if (!originalFileContents.contains(file)) {
            originalFileContents[file] = file.text
        }
    }

    private fun getChangedFilesWithContent(): Map<PsiFile, String> {
        return originalFileContents.filter { (f, originalContent) ->
            f.text != originalContent
        }
    }

    /**
     * Returns the files stored via [storeOriginalFileContent] whose file contents have changed.
     */
    fun getChangedFiles(): List<PsiFile> {
        return getChangedFilesWithContent().map { it.key }
    }

    fun collectChangedFiles(): List<KotlinConfiguratorChangedFile> {
        return getChangedFilesWithContent().map { (file, originalContent) ->
            KotlinConfiguratorChangedFile(file, originalContent, file.text)
        }
    }
}