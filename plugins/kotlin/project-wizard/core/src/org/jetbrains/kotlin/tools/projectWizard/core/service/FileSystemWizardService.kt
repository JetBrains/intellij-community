// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.core.service

import org.jetbrains.kotlin.tools.projectWizard.core.Success
import org.jetbrains.kotlin.tools.projectWizard.core.TaskResult
import org.jetbrains.kotlin.tools.projectWizard.core.computeM
import org.jetbrains.kotlin.tools.projectWizard.core.safe
import java.nio.file.Path
import kotlin.io.path.*

interface FileSystemWizardService : WizardService {
    fun createFile(path: Path, text: String): TaskResult<Unit>
    fun createDirectory(path: Path): TaskResult<Unit>

    fun renderPath(path: Path): String = path.toString().replace("""\""", """\\""")
}

class OsFileSystemWizardService : FileSystemWizardService, IdeaIndependentWizardService {
    override fun createFile(path: Path, text: String) = computeM {
        if (path.exists()) return@computeM Success(Unit)
        createDirectory(path.parent).ensure()
        safe { path.normalize().createFile().writeText(text) }
    }

    override fun createDirectory(path: Path) = safe {
        @Suppress("NAME_SHADOWING") val path = path.normalize()
        if (path.notExists()) {
            path.createDirectories()
        }
    }
}
