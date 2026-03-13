// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.TextWithImports
import com.intellij.debugger.engine.executeOnDMT
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.JavaCodeFragment
import com.intellij.psi.PsiElement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.projectStructure.refinedContextModule
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypePointer
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleWithElementSourceModuleKindOrProduction
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.debugger.core.CodeFragmentContextTuner
import org.jetbrains.kotlin.idea.debugger.evaluate.util.KotlinK2CodeFragmentUtils
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtExpression
import kotlin.time.Duration.Companion.milliseconds

class KotlinK2CodeFragmentFactory : KotlinCodeFragmentFactoryBase() {
    override fun createPsiCodeFragmentImpl(item: TextWithImports, context: PsiElement?, project: Project): JavaCodeFragment {
        val contextElement = CodeFragmentContextTuner.getInstance().tuneContextElement(context)
        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", item.text, initImports(item.imports), contextElement).apply {
            /*
            Handle support for KMP:
            If the given module has refining (modules that have a 'refines' edge to this module) modules,
            Then we'll try to find a leaf jvm module which we can use as context for evaluating the expressions.
             */
            if (contextElement != null) {
                val jvmLeafModule = contextElement.module?.implementingModules
                    .orEmpty()
                    .filter { module -> module.implementingModules.isEmpty() } // Looking for a leave
                    .firstOrNull { module -> module.platform.isJvm() }

                // This is a workaround and should be removed in the future. See IDEA-385152.
                @OptIn(KaImplementationDetail::class)
                refinedContextModule = jvmLeafModule?.toKaSourceModuleWithElementSourceModuleKindOrProduction(contextElement)
            }
        }
        codeFragment.registerCodeFragmentExtensions(contextElement)
        return codeFragment
    }

    @OptIn(KaExperimentalApi::class)
    override fun KtBlockCodeFragment.registerCodeFragmentExtensions(contextElement: PsiElement?) {
        putCopyableUserData(KotlinK2CodeFragmentUtils.RUNTIME_TYPE_EVALUATOR_K2) { expression: KtExpression ->
            val debuggerContext = DebuggerManagerEx.getInstanceEx(project).context
            val debuggerSession = debuggerContext.debuggerSession
            val managerThread = debuggerContext.managerThread
            if (debuggerSession == null || debuggerContext.suspendContext == null || managerThread == null) {
                null
            } else {
                LOG.assertTrue(!DebuggerManagerThreadImpl.isManagerThread(), "Should be invoked outside manager thread")
                val timeout = Registry.intValue("debugger.evaluation.runtime.type", 2000).milliseconds
                val runtimeType = CompletableDeferred<KaTypePointer<KaType>?>()
                val job = executeOnDMT(managerThread) {
                    coroutineToIndicator { indicator ->
                        debuggerContext.managerThread?.invokeNow(
                            object : KotlinK2RuntimeTypeEvaluator(
                                null,
                                expression,
                                debuggerContext,
                                indicator,
                            ) {
                                override fun typeCalculationFinished(type: KaTypePointer<KaType>?) {
                                    runtimeType.complete(type)
                                }

                                override fun commandCancelled() {
                                    runtimeType.complete(null)
                                }
                            }
                        )
                    }
                }
                runBlockingCancellable {
                    try {
                        withTimeout(timeout) {
                            runtimeType.await()
                        }
                    } catch (_: TimeoutCancellationException) {
                        job.cancel()
                        LOG.error("Timeout while waiting for runtime type evaluation of ${expression.text}")
                        null
                    }
                }
            }
        }
    }

    override fun isContextAccepted(contextElement: PsiElement?): Boolean {
        return contextElement?.language == KotlinFileType.INSTANCE.language
    }
}