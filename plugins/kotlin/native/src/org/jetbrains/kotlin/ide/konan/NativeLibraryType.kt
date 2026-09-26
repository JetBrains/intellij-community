// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.ide.konan

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.DummyLibraryProperties
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.platforms.KotlinNativeLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.isKlibLibraryRootForPlatform
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import javax.swing.Icon
import javax.swing.JComponent

internal class NativeLibraryType : LibraryType<DummyLibraryProperties>(KotlinNativeLibraryKind) {
    override fun createPropertiesEditor(editorComponent: LibraryEditorComponent<DummyLibraryProperties>): Nothing? = null
    override fun getCreateActionName(): Nothing? = null
    override fun createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile?, project: Project): Nothing? = null

    // Library type is determined by `KotlinGradleLibraryDataService` for every library dependency imported from Gradle to IDE.
    // However this does not work for libraries that are to be just created during project build, e.g. C-interop Kotlin/Native KLIBs.
    // The code below helps to perform postponed detection of Kotlin/Native libraries.
    override fun detect(classesRoots: List<VirtualFile>): DummyLibraryProperties? =
        if (classesRoots.firstOrNull()?.isKlibLibraryRootForPlatform(NativePlatforms.unspecifiedNativePlatform) == true)
            DummyLibraryProperties.INSTANCE!!
        else null

    override fun getIcon(properties: DummyLibraryProperties?): Icon = KotlinIcons.NATIVE
}
