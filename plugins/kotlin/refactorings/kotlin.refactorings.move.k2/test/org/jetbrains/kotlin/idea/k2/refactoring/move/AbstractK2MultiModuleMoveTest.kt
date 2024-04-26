// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.refactoring.move.AbstractMultiModuleMoveTest
import org.jetbrains.kotlin.idea.refactoring.runRefactoringTest

abstract class AbstractK2MultiModuleMoveTest : AbstractMultiModuleMoveTest() {
    override val isFirPlugin: Boolean = true

    override fun runRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
        val action = when (config.getString("type")) {
            "MOVE_FILES", "MOVE_FILES_WITH_DECLARATIONS" -> K2MoveFileRefactoringAction
            "MOVE_KOTLIN_TOP_LEVEL_DECLARATIONS" -> K2MoveTopLevelRefactoringAction
            "MOVE_PACKAGES" -> K2MovePackageRefactoringAction
            else -> throw IllegalArgumentException("Unsupported move refactoring type")
        }
        runRefactoringTest(path, config, rootDir, project, action)
    }
}
