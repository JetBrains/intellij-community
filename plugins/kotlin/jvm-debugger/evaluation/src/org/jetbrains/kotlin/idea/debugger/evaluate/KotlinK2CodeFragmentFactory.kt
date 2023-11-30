// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.evaluation.CodeFragmentFactory
import com.intellij.debugger.engine.evaluation.TextWithImports
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaCodeFragment
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.debugger.core.CodeFragmentContextTuner
import org.jetbrains.kotlin.psi.KtBlockCodeFragment

class KotlinK2CodeFragmentFactory : CodeFragmentFactory() {
    override fun createCodeFragment(item: TextWithImports, context: PsiElement?, project: Project): JavaCodeFragment {
        val contextElement = CodeFragmentContextTuner.getInstance().tuneContextElement(context)
        return KtBlockCodeFragment(project, "fragment.kt", item.text, item.imports, contextElement)
    }

    override fun createPresentationCodeFragment(item: TextWithImports, context: PsiElement?, project: Project): JavaCodeFragment {
        return createCodeFragment(item, context, project)
    }

    override fun isContextAccepted(contextElement: PsiElement?): Boolean {
        return contextElement?.language == KotlinFileType.INSTANCE.language
    }

    override fun getFileType(): KotlinFileType {
        return KotlinFileType.INSTANCE
    }

    override fun getEvaluatorBuilder(): KotlinEvaluatorBuilder {
        return KotlinEvaluatorBuilder
    }
}