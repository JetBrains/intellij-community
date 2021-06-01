// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor
import org.jetbrains.kotlin.idea.framework.JSLibraryKind
import org.jetbrains.kotlin.idea.framework.JSLibraryStdDescription

object KotlinStdJSProjectDescriptor : KotlinLightProjectDescriptor() {
    override fun getSdk(): Sdk? = null

    override fun configureModule(module: Module, model: ModifiableRootModel) {
        val configuration = JSLibraryStdDescription(module.project).createNewLibraryForTests()

        val editor = NewLibraryEditor(configuration.libraryType, configuration.properties)
        configuration.addRoots(editor)

        ConfigLibraryUtil.addLibrary(editor, model, JSLibraryKind)
    }
}