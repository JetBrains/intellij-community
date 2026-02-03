// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor

import com.intellij.openapi.project.Project

sealed class K2MoveDescriptor(
    open val project: Project,
    open val source: K2MoveSourceDescriptor<*>,
    open val target: K2MoveTargetDescriptor,
) {
    class Files(
        override val project: Project,
        override val source: K2MoveSourceDescriptor.FileSource,
        override val target: K2MoveTargetDescriptor.Directory
    ) : K2MoveDescriptor(project, source, target)

    class Declarations(
      override val project: Project,
      override val source: K2MoveSourceDescriptor.ElementSource,
      override val target: K2MoveTargetDescriptor.Declaration<*>
    ) : K2MoveDescriptor(project, source, target)
}