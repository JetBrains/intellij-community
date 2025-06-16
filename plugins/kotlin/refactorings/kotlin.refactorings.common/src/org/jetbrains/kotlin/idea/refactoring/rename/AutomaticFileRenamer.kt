// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.util.NlsContexts.*
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVariableDeclaration

class AutomaticFileRenamer(
    file: KtFile,
    newFileName: String,
) : AutomaticRenamer() {

    init {
        myElements.add(file)
        suggestAllNames(file.name, "$newFileName.${file.virtualFile.extension ?: KotlinFileType.EXTENSION}")
    }

    override fun allowChangeSuggestedName(): Boolean = false

    override fun getDialogTitle(): @DialogTitle String = KotlinBundle.message("title.rename.file")

    override fun getDialogDescription(): @Button String = KotlinBundle.message("title.rename.file.to")

    override fun entityName(): @ColumnName String = KotlinBundle.message("file.entity")

    override fun isSelectedByDefault(): Boolean = true
}

open class AutomaticFileRenamerFactory : AutomaticRenamerFactory {
    override fun isApplicable(element: PsiElement): Boolean {
        if (!(element is KtNamedFunction || element is KtVariableDeclaration)) return false
        val file = element.containingFile as? KtFile ?: return false

        val declaration = file.declarations.singleOrNull() as? KtNamedDeclaration ?: return false
        return declaration.name == file.virtualFile.nameWithoutExtension && element == declaration
    }

    override fun createRenamer(element: PsiElement, newName: String, usages: Collection<UsageInfo>): AutomaticFileRenamer {
      return AutomaticFileRenamer(element.containingFile as KtFile, newName)
    }

    override fun isEnabled() = KotlinCommonRefactoringSettings.getInstance().renameFileNames

    override fun setEnabled(enabled: Boolean) {
        KotlinCommonRefactoringSettings.getInstance().renameFileNames = enabled
    }

    override fun getOptionName(): String? = KotlinBundle.message("rename.file")
}
