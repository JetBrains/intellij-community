// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
class RemoveReceiverParameterFix<T : KtCallableDeclaration>(
    callableDeclaration: T,
    private val textForReceiver: String? = null,
) : KotlinQuickFixAction<T>(callableDeclaration) {
    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val callableDeclaration = element ?: return
        ReceiverParameterChangeSignatureUtils.removeReceiverParameter(project, callableDeclaration, textForReceiver)
    }

    override fun startInWriteAction(): Boolean = false
    override fun getText(): String = familyName
    override fun getFamilyName(): String = KotlinBundle.message("fix.unused.receiver.parameter.remove")
}
