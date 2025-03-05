// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.JavaDebuggerCodeFragmentFactory
import com.intellij.debugger.engine.evaluation.TextWithImports
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaCodeFragment
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.projectStructure.analysisContextModule
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProductionOrTest
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.debugger.core.CodeFragmentContextTuner
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtBlockCodeFragment

class KotlinK2CodeFragmentFactory : JavaDebuggerCodeFragmentFactory() {
    @OptIn(KaImplementationDetail::class)
    override fun createPsiCodeFragmentImpl(item: TextWithImports, context: PsiElement?, project: Project): JavaCodeFragment {
        val contextElement = CodeFragmentContextTuner.getInstance().tuneContextElement(context)

        return KtBlockCodeFragment(project, "fragment.kt", item.text, item.imports, contextElement).apply {
            /*
            Handle support for KMP:
            If the given module has refining (modules that have a 'refines' edge to this module) modules,
            Then we'll try to find a leaf jvm module which we can use as context for evaluating the expressions.
             */
            val jvmLeafModule = contextElement?.module?.implementingModules
                .orEmpty()
                .filter { module -> module.implementingModules.isEmpty() } // Looking for a leave
                .firstOrNull { module -> module.platform.isJvm() }

            virtualFile.analysisContextModule = jvmLeafModule?.toKaSourceModuleForProductionOrTest()
        }
    }

    override fun createPresentationPsiCodeFragmentImpl(item: TextWithImports, context: PsiElement?, project: Project): JavaCodeFragment? {
        return createPsiCodeFragment(item, context, project)
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