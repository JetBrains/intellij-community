// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.DirectoryAsPackageRenameHandler
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded

/**
 * Allows to use [DirectoryAsPackageRenameHandler]'s logic even if the name of the package is not a valid Java identifier.
 *
 * Without it, the platform will resort to a regular PSI element rename mechanism, which will not properly update packages.
 *
 * N.B. This implementation is supposed to replace the platform implementation of [DirectoryAsPackageRenameHandler],
 * so it has to be registered after it, so it can replace it in [com.intellij.refactoring.rename.RenameHandlerRegistry].
 */
class KotlinDirectoryAsPackageRenameHandler : DirectoryAsPackageRenameHandler() {
    override fun isIdentifier(name: String, project: Project): Boolean = name.quoteIfNeeded().isIdentifier()
}