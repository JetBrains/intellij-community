// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import org.jetbrains.kotlin.idea.refactoring.safeDelete.AbstractMultiModuleSafeDeleteTest
import org.jetbrains.kotlin.idea.test.KotlinTestUtils

abstract class AbstractFirMultiModuleSafeDeleteTest: AbstractMultiModuleSafeDeleteTest() {
    override fun isFirPlugin(): Boolean {
        return true
    }

    override fun getAlternativeConflictsFile(): String? {
        return "conflicts.k2.txt"
    }

    override fun fileFilter(file: VirtualFile): Boolean {
        if (file.isFile && file.extension == "kt") {
            if (file.name.endsWith(".k2.kt")) return true
            val k2CounterPart = file.parent.findChild("${file.nameWithoutExtension}.k2.kt")
            if (k2CounterPart?.isFile == true) return false
        }
        return !KotlinTestUtils.isMultiExtensionName(file.name)
    }
}