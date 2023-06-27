// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor

class K2MoveFilesRefactoringProcessor(val descriptor: K2MoveDescriptor.Files) : MoveFilesOrDirectoriesProcessor(
    descriptor.target.pkg.project,
    descriptor.source.elements.toTypedArray(),
    descriptor.target.directory,
    descriptor.searchReferences,
    descriptor.searchInComments,
    descriptor.searchForText,
    MoveCallback {  },
    Runnable {  }
) {


}