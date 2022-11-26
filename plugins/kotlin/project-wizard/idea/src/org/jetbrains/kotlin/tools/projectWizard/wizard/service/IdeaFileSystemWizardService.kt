// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard.service

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.kotlin.tools.projectWizard.core.TaskResult
import org.jetbrains.kotlin.tools.projectWizard.core.safe
import org.jetbrains.kotlin.tools.projectWizard.core.service.FileSystemWizardService
import java.nio.file.Path
import kotlin.io.path.pathString

class IdeaFileSystemWizardService : FileSystemWizardService, IdeaWizardService {
    override fun createDirectory(path: Path): TaskResult<Unit> = safe {
        runWriteAction<Unit> {
            VfsUtil.createDirectoryIfMissing(path.pathString)
        }
    }

    override fun createFile(path: Path, text: String): TaskResult<Unit> = safe {
        runWriteAction {
            val directory = VfsUtil.createDirectoryIfMissing(path.parent.pathString)!!
            val virtualFile = directory.createChildData(this, path.fileName.toString())
            VfsUtil.saveText(virtualFile, StringUtil.convertLineSeparators(text))
        }
    }
}