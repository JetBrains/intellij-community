// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectConfiguration

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryKind

class CommonStandardLibraryDescription(project: Project?) : CustomLibraryDescriptionWithDeferredConfig(
    project,
    configuratorName = "common",
    libraryName = "KotlinStdlibCommon",
    libraryKind = KOTLIN_COMMON_STDLIB_KIND,
    suitableLibraryKinds = SUITABLE_LIBRARY_KINDS
) {
    private companion object {
        private val KOTLIN_COMMON_STDLIB_KIND: LibraryKind = LibraryKind.create("kotlin-stdlib-common")
        private val SUITABLE_LIBRARY_KINDS: Set<LibraryKind> = setOf(KOTLIN_COMMON_STDLIB_KIND)
    }
}