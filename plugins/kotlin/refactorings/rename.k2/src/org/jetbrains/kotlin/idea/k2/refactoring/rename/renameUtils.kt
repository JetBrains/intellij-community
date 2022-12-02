// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.refactoring.rename.api.ModifiableRenameUsage
import com.intellij.refactoring.rename.api.fileRangeUpdater
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded

internal fun kotlinFileRangeUpdater(): ModifiableRenameUsage.FileUpdater =
    fileRangeUpdater { newName -> newName.quoteIfNeeded() }
