// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard.service

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.tools.projectWizard.core.service.FileFormattingService
import java.io.File

class IdeaFileFormattingService(private val project: Project) : FileFormattingService, IdeaWizardService {
    override fun formatFile(text: String, filename: String): String = runReadAction {
        val psiFile = createPsiFile(StringUtil.convertLineSeparators(text), filename) ?: return@runReadAction text
        CodeStyleManager.getInstance(project).reformat(psiFile).text
    }

    private fun createPsiFile(text: String, filename: String) = when (File(filename.removeSuffix(".vm")).extension) {
        "kt" -> KtPsiFactory(project).createFile(text)
        else -> null
    }
}