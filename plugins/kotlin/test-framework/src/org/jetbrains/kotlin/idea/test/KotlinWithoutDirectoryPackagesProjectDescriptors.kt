// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes

/**
 * Sets `packageMatchesDirectory` to `false` on the Java source folders of the light module.
 * A directory under such a source folder has no package.
 * The `// NO_DIRECTORY_PACKAGES` directive selects this descriptor.
 */
object KotlinWithoutDirectoryPackagesProjectDescriptor : KotlinWithJdkAndRuntimeLightProjectDescriptor() {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        super.configureModule(module, model, contentEntry)
        for (sourceFolder in contentEntry.sourceFolders) {
            sourceFolder.jpsElement.getProperties(JavaModuleSourceRootTypes.SOURCES)?.isPackageMatchesDirectory = false
        }
    }
}

/**
 * Adds a source root that is the single file [FILE_PATH], next to the light source root.
 * The directory of that file is not in source content, so it has no package.
 * The `// SINGLE_FILE_SOURCE_ROOT` directive selects this descriptor.
 */
object KotlinSingleFileSourceRootProjectDescriptor : KotlinWithJdkAndRuntimeLightProjectDescriptor() {
    /** The path of the single-file source root, relative to the parent of the light source root. */
    const val FILE_PATH: String = "singleFileSourceRoot/a/b/Foo.kt"

    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        super.configureModule(module, model, contentEntry)
        val parent = contentEntry.file?.parent ?: error("The light content root has no parent directory")
        val file = VfsTestUtil.createFile(parent, FILE_PATH, "")
        model.addContentEntry(file).addSourceFolder(file, false)
    }

    /** Returns the single-file source root of [module]. */
    fun sourceRootFile(module: Module): VirtualFile =
        ModuleRootManager.getInstance(module).sourceRoots.single { !it.isDirectory }
}
