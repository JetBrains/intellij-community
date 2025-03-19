package org.toml.ide.json

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile

interface TomlJsonSchemaCompletionFileFilter {
    companion object {
        val EP_NAME = ExtensionPointName.create<TomlJsonSchemaCompletionFileFilter>("org.toml.ide.json.tomlJsonSchemaCompletionFileFilter")

        fun shouldCompleteInFile(file: PsiFile): Boolean = EP_NAME.extensionsIfPointIsRegistered.all { it.shouldCompleteInFile(file) }
    }

    fun shouldCompleteInFile(file: PsiFile): Boolean
}