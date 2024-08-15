// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

class K2ChangePackageDescriptor(
    val project: Project,
    val files: Set<KtFile>,
    val target: FqName,
    val searchForText: Boolean
)