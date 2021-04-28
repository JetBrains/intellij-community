/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.kotlin.idea.framework.ui

import com.google.common.collect.ImmutableMap
import com.intellij.ide.util.projectWizard.ProjectWizardUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.idea.KotlinJvmBundle.message
import java.io.File
import java.io.IOException
import javax.swing.JOptionPane

object FileUIUtils {
    fun copyWithOverwriteDialog(
        messagesTitle: String,
        destinationFolder: String,
        file: File
    ): File? = copyWithOverwriteDialog(messagesTitle, ImmutableMap.of(file, destinationFolder))?.getValue(file)

    fun copyWithOverwriteDialog(
        messagesTitle: String,
        filesWithDestinations: Map<File, String>
    ): Map<File, File>? {
        val fileNames = mutableSetOf<String>()
        val targetFiles = LinkedHashMap<File, File>(filesWithDestinations.size)
        for ((file, destinationPath) in filesWithDestinations) {
            val fileName = file.name
            require(fileNames.add(fileName)) { "There are several files with the same name: $fileName" }
            targetFiles[file] = File(destinationPath, fileName)
        }

        val existentFiles = targetFiles.entries.filter { (_, value) -> value.exists() }
        if (existentFiles.isNotEmpty()) {
            val message: String = if (existentFiles.size == 1) {
                val conflictingFile = existentFiles.iterator().next().value
                message(
                    "file.exists.single",
                    conflictingFile.name, conflictingFile.parentFile.absolutePath
                )
            } else {
                val conflictFiles: Collection<File?> = existentFiles.map { (_, value) -> value }
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
                val destinationPath = value.parentFile.absolutePath
                if (!ProjectWizardUtil.createDirectoryIfNotExists(message("file.destination.folder"), destinationPath, false)) {
                    Messages.showErrorDialog(message("file.error.new.folder", destinationPath), messagesTitle)
                    return null
                }
                FileUtil.copy(key, value)
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(value)
            } catch (e: IOException) {
                Messages.showErrorDialog(message("file.error.copy", key.name), messagesTitle)
                return null
            }
        }
        return targetFiles
    }

    fun createRelativePath(project: Project?, contextDirectory: VirtualFile?, relativePath: String?): String {
        var path: String? = null
        if (contextDirectory != null) {
            path = PathUtil.getLocalPath(contextDirectory)
        } else if (project != null) {
            path = PathUtil.getLocalPath(project.baseDir)
        }
        path = if (path != null) {
            File(path, relativePath).absolutePath
        } else {
            ""
        }
        return path!!
    }
}