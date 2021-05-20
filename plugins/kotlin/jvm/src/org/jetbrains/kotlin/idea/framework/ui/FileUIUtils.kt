// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.framework.ui

import com.google.common.collect.ImmutableMap
import com.intellij.ide.util.projectWizard.ProjectWizardUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.idea.KotlinJvmBundle.message
import java.io.IOException
import java.nio.file.Path
import javax.swing.JOptionPane
import kotlin.io.path.*

object FileUIUtils {
    fun copyWithOverwriteDialog(
        messagesTitle: String,
        destinationFolder: String,
        file: Path
    ): Path? = copyWithOverwriteDialog(messagesTitle, ImmutableMap.of(file, destinationFolder))?.getValue(file)

    fun copyWithOverwriteDialog(
        messagesTitle: String,
        filesWithDestinations: Map<Path, String>
    ): Map<Path, Path>? {
        val fileNames = mutableSetOf<String>()
        val targetFiles = LinkedHashMap<Path, Path>(filesWithDestinations.size)
        for ((file, destinationPath) in filesWithDestinations) {
            val fileName = file.name
            require(fileNames.add(fileName)) { "There are several files with the same name: $fileName" }
            targetFiles[file] = Path(destinationPath, fileName)
        }

        val existentFiles = targetFiles.entries.filter { (_, value) -> value.exists() }
        if (existentFiles.isNotEmpty()) {
            val message: String = if (existentFiles.size == 1) {
                val conflictingFile = existentFiles.iterator().next().value
                message(
                    "file.exists.single",
                    conflictingFile.name, conflictingFile.parent.absolutePathString()
                )
            } else {
                val conflictFiles: Collection<Path?> = existentFiles.map { (_, value) -> value }
                message("file.exists", StringUtil.join(conflictFiles, "\n"))
            }

            val replaceIfExist = Messages.showYesNoDialog(
                null,
                message,
                messagesTitle + message("file.overwrite.title"),
                message("file.overwrite.overwrite"),
                message("file.overwrite.cancel"),
                Messages.getWarningIcon()
            )
            if (replaceIfExist != JOptionPane.YES_OPTION) {
                return null
            }
        }

        for ((key, value) in targetFiles) {
            try {
                val destinationPath = value.parent.absolutePathString()
                if (!ProjectWizardUtil.createDirectoryIfNotExists(message("file.destination.folder"), destinationPath, false)) {
                    Messages.showErrorDialog(message("file.error.new.folder", destinationPath), messagesTitle)
                    return null
                }

                key.copyTo(value, overwrite = true)
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(value)
            } catch (e: IOException) {
                Messages.showErrorDialog(message("file.error.copy", key.name), messagesTitle)
                return null
            }
        }
        return targetFiles
    }

    fun createRelativePath(project: Project?, contextDirectory: VirtualFile?, relativePath: String): String = when {
        contextDirectory != null -> PathUtil.getLocalPath(contextDirectory)
        project != null -> PathUtil.getLocalPath(project.baseDir)
        else -> null
    }?.let { Path(it, relativePath).absolutePathString() } ?: ""
}