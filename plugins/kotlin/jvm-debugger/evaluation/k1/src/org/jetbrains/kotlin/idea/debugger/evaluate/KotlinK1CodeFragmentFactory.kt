// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.JavaDebuggerCodeFragmentFactory
import com.intellij.debugger.engine.evaluation.TextWithImports
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.concurrency.Semaphore
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.InvalidStackFrameException
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.hasKotlinJvmRuntime
import org.jetbrains.kotlin.idea.core.syncNonBlockingReadAction
import org.jetbrains.kotlin.idea.core.util.CodeFragmentUtils
import org.jetbrains.kotlin.idea.debugger.base.util.hopelessAware
import org.jetbrains.kotlin.idea.debugger.core.CodeFragmentContextTuner
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.DebugForeignPropertyDescriptorProvider
import org.jetbrains.kotlin.j2k.OldJ2kPostProcessor
import org.jetbrains.kotlin.idea.j2k.convertToKotlin
import org.jetbrains.kotlin.idea.j2k.j2kText
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.j2k.AfterConversionPass
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.types.KotlinType
import java.util.concurrent.atomic.AtomicReference

class KotlinK1CodeFragmentFactory : JavaDebuggerCodeFragmentFactory() {
    override fun createPsiCodeFragmentImpl(item: TextWithImports, context: PsiElement?, project: Project): JavaCodeFragment {
        val contextElement = CodeFragmentContextTuner.getInstance().tuneContextElement(context)

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", item.text, initImports(item.imports), contextElement)

        codeFragment.putCopyableUserData(CodeFragmentUtils.RUNTIME_TYPE_EVALUATOR) { expression: KtExpression ->
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
            codeFragment.putCopyableUserData(KtCodeFragment.FAKE_CONTEXT_FOR_JAVA_FILE) {
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

        supplyDebugInformation(codeFragment, context)

        return codeFragment
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

        val worker = object : DebuggerCommandImpl() {
            override fun action() {
                try {
                    val frameProxy = hopelessAware {
                        if (isUnitTestMode()) {
                            DebugContextProvider.getDebuggerContext(project, contextElement)?.frameProxy
                        } else {
                            debuggerContext.frameProxy
                        }
                    }

                    frameInfo = FrameInfo.from(debuggerContext.project, frameProxy)
                } catch (ignored: AbsentInformationException) {
                    // Debug info unavailable
                } catch (ignored: InvalidStackFrameException) {
                    // Thread is resumed, the frame we have is not valid anymore
                } finally {
                    semaphore.up()
                }
            }
        }

        debuggerContext.managerThread?.invoke(worker)

        for (i in 0..50) {
            if (semaphore.waitFor(20)) break
        }

        return frameInfo
    }

    private fun initImports(imports: String?): String? {
        if (!imports.isNullOrEmpty()) {
            return imports.split(KtCodeFragment.IMPORT_SEPARATOR)
                .mapNotNull { fixImportIfNeeded(it) }
                .joinToString(KtCodeFragment.IMPORT_SEPARATOR)
        }
        return null
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

    override fun createPresentationPsiCodeFragmentImpl(item: TextWithImports, context: PsiElement?, project: Project): JavaCodeFragment? {
        val kotlinCodeFragment = createPsiCodeFragment(item, context, project) ?: return null
        if (PsiTreeUtil.hasErrorElements(kotlinCodeFragment) && kotlinCodeFragment is KtCodeFragment) {
            val javaExpression = try {
                PsiElementFactory.getInstance(project).createExpressionFromText(item.text, context)
            } catch (e: IncorrectOperationException) {
                null
            }

            val importList = try {
                kotlinCodeFragment.importsAsImportList()?.let {
                    (PsiFileFactory.getInstance(project).createFileFromText(
                        "dummy.java", JavaFileType.INSTANCE, it.text
                    ) as? PsiJavaFile)?.importList
                }
            } catch (e: IncorrectOperationException) {
                null
            }

            if (javaExpression != null && !PsiTreeUtil.hasErrorElements(javaExpression)) {
                var convertedFragment: KtExpressionCodeFragment? = null
                project.executeWriteCommand(KotlinDebuggerEvaluationBundle.message("j2k.expression")) {
                    try {
                        val (elementResults, _, conversionContext) = javaExpression.convertToKotlin() ?: return@executeWriteCommand
                        val newText = elementResults.singleOrNull()?.text
                        val newImports = importList?.j2kText()
                        if (newText != null) {
                            convertedFragment = KtExpressionCodeFragment(
                                project,
                                kotlinCodeFragment.name,
                                newText,
                                newImports,
                                kotlinCodeFragment.context
                            )

                            AfterConversionPass(project, OldJ2kPostProcessor(formatCode = false))
                                .run(
                                    convertedFragment!!,
                                    conversionContext,
                                    range = null,
                                    onPhaseChanged = null
                                )
                        }
                    } catch (e: Throwable) {
                        // ignored because text can be invalid
                        LOG.error("Couldn't convert expression:\n`${javaExpression.text}`", e)
                    }
                }
                return convertedFragment ?: kotlinCodeFragment
            }
        }
        return kotlinCodeFragment
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

    override fun getFileType(): KotlinFileType = KotlinFileType.INSTANCE

    override fun getEvaluatorBuilder() = KotlinEvaluatorBuilder

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
