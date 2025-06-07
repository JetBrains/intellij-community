// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.pom.java.LanguageLevel
import java.io.File

open class KotlinJdkAndLibraryProjectDescriptor(
    val libraryFiles: List<File>,
    val librarySourceFiles: List<File> = emptyList(),
    val javaLanguageVersion: LanguageLevel? = null,
) : KotlinLightProjectDescriptor() {

    constructor(libraryFile: File) : this(listOf(libraryFile))

    init {
        for (libraryFile in libraryFiles + librarySourceFiles) {
            assert(libraryFile.exists()) { "Library file doesn't exist: " + libraryFile.absolutePath }
        }
    }

    override fun configureModule(module: Module, model: ModifiableRootModel) {
        ConfigLibraryUtil.addLibrary(model, LIBRARY_NAME) {
            for (libraryFile in libraryFiles) {
                addRoot(libraryFile, OrderRootType.CLASSES)
            }
            for (librarySrcFile in librarySourceFiles) {
                addRoot(librarySrcFile, OrderRootType.SOURCES)
            }
        }

        model.getModuleExtension(LanguageLevelModuleExtension::class.java).setLanguageLevel(javaLanguageVersion ?: LanguageLevel.HIGHEST);
    }

    companion object {
        const val LIBRARY_NAME = "myLibrary"
    }
}
