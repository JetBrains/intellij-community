// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor

class K2MoveFilesOrDirectoriesRefactoringProcessor(descriptor: K2MoveDescriptor.Files) : MoveFilesOrDirectoriesProcessor(
    descriptor.target.pkg.project,
    descriptor.source.elements.toTypedArray(),
    descriptor.target.directory,
    descriptor.searchReferences,
    descriptor.searchInComments,
    descriptor.searchForText,
    MoveCallback {  },
    Runnable {  }
)