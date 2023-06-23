// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import org.jetbrains.kotlin.idea.refactoring.move.KotlinMoveSource

class K2MoveDescriptor(
    val source: KotlinMoveSource,
    val target: K2MoveTarget,
    val searchReferences: Boolean,
    val deleteEmptySourceFiles: Boolean,
    val searchTextOccurrences: Boolean,
    val searchCommentsAndStrings: Boolean
) {
    val project get() = target.pkg.project
}