// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.navigation

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import java.io.File

abstract class AbstractKotlinGotoImplementationMultiModuleTest : AbstractKotlinNavigationMultiModuleTest() {
    override fun getTestDataDirectory(): File = IDEA_TEST_DATA_DIR.resolve("navigation/implementations/multiModule")

    override fun doNavigate(editor: Editor, file: PsiFile) = NavigationTestUtils.invokeGotoImplementations(editor, file)!!
}