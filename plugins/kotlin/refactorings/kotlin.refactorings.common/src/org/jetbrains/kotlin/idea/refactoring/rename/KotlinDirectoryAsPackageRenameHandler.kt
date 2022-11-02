// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.DirectoryAsPackageRenameHandler
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded

class KotlinDirectoryAsPackageRenameHandler : DirectoryAsPackageRenameHandler() {
    override fun isIdentifier(name: String, project: Project): Boolean = name.quoteIfNeeded().isIdentifier()
}