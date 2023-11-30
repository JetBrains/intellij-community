// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.TextRevisionNumber
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle

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

    private fun createRevision(contents: String, virtualFile: VirtualFile, name: String): ContentRevision {
        return object: ContentRevision {
            override fun getContent(): String = contents

            override fun getFile(): FilePath = VcsUtil.getFilePath(virtualFile)

            override fun getRevisionNumber(): VcsRevisionNumber = TextRevisionNumber(name)

        }
    }

    fun calculateChanges(): List<Change> {
        return getChangedFilesWithContent().mapNotNull { (f, originalContent) ->
            val virtualFile = f.virtualFile ?: return@mapNotNull null
            val originalRevision = createRevision(originalContent, virtualFile, KotlinProjectConfigurationBundle.message("configure.kotlin.original.content"))
            val newRevision = createRevision(f.text, virtualFile, KotlinProjectConfigurationBundle.message("configure.kotlin.modified.content"))
            Change(originalRevision, newRevision, FileStatus.MODIFIED)
        }
    }
}