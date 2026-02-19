// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.evaluation.TextWithImports
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.JavaCodeFragment
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.util.concurrency.Semaphore
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.InvalidStackFrameException
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.hasKotlinJvmRuntime
import org.jetbrains.kotlin.idea.core.syncNonBlockingReadAction
import org.jetbrains.kotlin.idea.core.util.CodeFragmentUtils
import org.jetbrains.kotlin.idea.debugger.base.util.hopelessAware
import org.jetbrains.kotlin.idea.debugger.core.CodeFragmentContextTuner
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.DebugForeignPropertyDescriptorProvider
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.types.KotlinType
import java.util.concurrent.atomic.AtomicReference

@K1Deprecation
class KotlinK1CodeFragmentFactory : KotlinCodeFragmentFactoryBase() {
    override fun createPsiCodeFragmentImpl(item: TextWithImports, context: PsiElement?, project: Project): JavaCodeFragment {
        val contextElement = CodeFragmentContextTuner.getInstance().tuneContextElement(context)

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", item.text, initImports(item.imports), contextElement)

        codeFragment.registerCodeFragmentExtensions(contextElement)

        supplyDebugInformation(codeFragment, context)

        return codeFragment
    }

    override fun KtBlockCodeFragment.registerCodeFragmentExtensions(contextElement: PsiElement?) {
        putCopyableUserData(CodeFragmentUtils.RUNTIME_TYPE_EVALUATOR) { expression: KtExpression ->
            val debuggerContext = DebuggerManagerEx.getInstanceEx(project).context
            val debuggerSession = debuggerContext.debuggerSession
            if (debuggerSession == null || debuggerContext.suspendContext == null) {
                null
            } else {
                val semaphore = Semaphore()
                semaphore.down()
                val nameRef = AtomicReference<KotlinType>()
                val worker = object : KotlinRuntimeTypeEvaluator(
                    null,
                    expression,
                    debuggerContext,
                    ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator(),
                ) {
                    override fun typeCalculationFinished(type: KotlinType?) {
                        nameRef.set(type)
                        semaphore.up()
                    }
                }

                debuggerContext.managerThread?.invoke(worker)

                for (i in 0..50) {
                    ProgressManager.checkCanceled()
                    if (semaphore.waitFor(20)) break
                }

                nameRef.get()
            }
        }

        if (contextElement != null && contextElement !is KtElement) {
            putCopyableUserData(KtCodeFragment.FAKE_CONTEXT_FOR_JAVA_FILE) {
                val emptyFile = createFakeFileWithJavaContextElement("", contextElement)

                val debuggerContext = DebuggerManagerEx.getInstanceEx(project).context
                val debuggerSession = debuggerContext.debuggerSession
                if ((debuggerSession == null || debuggerContext.suspendContext == null) &&
                    !isUnitTestMode()
                ) {
                    LOG.warn("Couldn't create fake context element for java file, debugger isn't paused on breakpoint")
                    return@putCopyableUserData emptyFile
                }

                val frameInfo = getFrameInfo(project, contextElement, debuggerContext) ?: run {
                    val position = "${debuggerContext.sourcePosition?.file?.name}:${debuggerContext.sourcePosition?.line}"
                    LOG.warn("Couldn't get info about 'this' and local variables for $position")
                    return@putCopyableUserData emptyFile
                }

                val fakeFunctionText = buildString {
                    append("fun ")

                    val thisType = frameInfo.thisObject?.asProperty()?.typeReference?.typeElement?.unwrapNullableType()
                    if (thisType != null) {
                        append(thisType.text).append('.')
                    }

                    append(FAKE_JAVA_CONTEXT_FUNCTION_NAME).append("() {\n")

                    for (variable in frameInfo.variables) {
                        val text = variable.asProperty()?.text ?: continue
                        append("    ").append(text).append("\n")
                    }

                    // There should be at least one declaration inside the function (or 'fakeContext' below won't work).
                    append("    val _debug_context_val = 1\n")

                    append("}")
                }

                val fakeFile = createFakeFileWithJavaContextElement(fakeFunctionText, contextElement)
                val fakeFunction = fakeFile.declarations.firstOrNull() as? KtFunction
                val fakeContext = fakeFunction?.bodyBlockExpression?.statements?.lastOrNull()

                return@putCopyableUserData fakeContext ?: emptyFile
            }
        }
    }

    private fun KtTypeElement.unwrapNullableType(): KtTypeElement {
        return if (this is KtNullableType) innerType ?: this else this
    }

    private fun supplyDebugInformation(codeFragment: KtCodeFragment, context: PsiElement?) {
        val project = codeFragment.project
        val debugProcess = DebugContextProvider.getDebuggerContext(project, context)?.debugProcess ?: return
        DebugForeignPropertyDescriptorProvider(codeFragment, debugProcess).supplyDebugForeignProperties()
    }

    private fun getFrameInfo(project: Project, contextElement: PsiElement?, debuggerContext: DebuggerContextImpl): FrameInfo? {
        val semaphore = Semaphore()
        semaphore.down()

        var frameInfo: FrameInfo? = null

        val managerThread = debuggerContext.managerThread
        // Should be invoked now if on DMT
        managerThread?.invoke(PrioritizedTask.Priority.LOW) {
            try {
                val frameProxy = hopelessAware {
                    if (isUnitTestMode()) {
                        DebugContextProvider.getDebuggerContext(project, contextElement)?.frameProxy
                    } else {
                        debuggerContext.frameProxy
                    }
                }

                frameInfo = FrameInfo.from(debuggerContext.project, frameProxy)
            } catch (_: AbsentInformationException) {
                // Debug info unavailable
            } catch (_: InvalidStackFrameException) {
                // Thread is resumed, the frame we have is not valid anymore
            } finally {
                semaphore.up()
            }
        }

        for (i in 0..50) {
            if (semaphore.waitFor(20)) break
        }

        return frameInfo
    }

    private fun fixImportIfNeeded(import: String): String? {
        // skip arrays
        if (import.endsWith("[]")) {
            return fixImportIfNeeded(import.removeSuffix("[]").trim())
        }

        // skip primitive types
        if (PsiTypesUtil.boxIfPossible(import) != import) {
            return null
        }
        return import
    }

    override fun isContextAccepted(contextElement: PsiElement?): Boolean = runReadAction {
        when {
            // PsiCodeBlock -> DummyHolder -> originalElement
            contextElement is PsiCodeBlock -> isContextAccepted(contextElement.context?.context)
            contextElement == null -> false
            contextElement.language == KotlinFileType.INSTANCE.language -> true
            contextElement.language == JavaFileType.INSTANCE.language && isJavaContextAccepted() -> {
                val project = contextElement.project
                val scope = contextElement.resolveScope
                syncNonBlockingReadAction(project) { scope.hasKotlinJvmRuntime(project) }
            }
            else -> false
        }
    }

    private fun isJavaContextAccepted(): Boolean = Registry.`is`("debugger.enable.kotlin.evaluator.in.java.context", false)

    companion object {
        private val LOG = Logger.getInstance(this::class.java)

        const val FAKE_JAVA_CONTEXT_FUNCTION_NAME = "_java_locals_debug_fun_"
    }

    private fun createFakeFileWithJavaContextElement(funWithLocalVariables: String, javaContext: PsiElement): KtFile {
        val javaFile = javaContext.containingFile as? PsiJavaFile

        val sb = StringBuilder()

        javaFile?.packageName?.takeUnless { it.isBlank() }?.let {
            sb.append("package ").append(it.quoteIfNeeded()).append("\n")
        }

        javaFile?.importList?.let { sb.append(it.text).append("\n") }

        sb.append(funWithLocalVariables)

        return KtPsiFactory.contextual(javaContext).createFile("fakeFileForJavaContextInDebugger.kt", sb.toString())
    }
}
