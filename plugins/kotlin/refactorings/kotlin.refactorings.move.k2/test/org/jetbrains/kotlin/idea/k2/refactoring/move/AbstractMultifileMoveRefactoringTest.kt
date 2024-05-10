// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import org.jetbrains.kotlin.idea.refactoring.AbstractMultifileRefactoringTest
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources

abstract class AbstractMultifileMoveRefactoringTest : AbstractMultifileRefactoringTest() {
    override fun getProjectDescriptor() = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

    override fun isEnabled(config: JsonObject): Boolean = config.get("enabledInK2")?.asBoolean == true

    override fun fileFilter(file: VirtualFile): Boolean {
        if (file.isFile && file.extension == "kt") {
            if (file.name.endsWith(".k2.kt")) return true
            val k2CounterPart = file.parent.findChild("${file.nameWithoutExtension}.k2.kt")
            if (k2CounterPart?.isFile == true) return false
        }
        return super.fileFilter(file)
    }

    override fun fileNameMapper(file: VirtualFile): String =
        file.name.replace(".k2.kt", ".kt")
}