// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.actions.MethodSmartStepTarget
import com.intellij.debugger.actions.SmartStepTarget
import com.intellij.debugger.engine.BasicStepMethodFilter
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.MethodFilter
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.engine.managerThread.DebuggerCommand
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.JvmSteppingCommandProvider
import com.intellij.debugger.impl.PositionUtil
import com.intellij.debugger.ui.impl.watch.MethodsTracker
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.psi.PsiElement
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.xdebugger.XDebuggerTestUtil
import com.intellij.xdebugger.frame.XStackFrame
import junit.framework.AssertionFailedError
import junit.framework.TestCase
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.kotlin.idea.debugger.KotlinPositionManager
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.KotlinStackFrame
import org.jetbrains.kotlin.idea.debugger.core.stepping.KotlinSteppingCommandProvider
import org.jetbrains.kotlin.idea.debugger.getContainingMethod
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.KotlinSmartStepIntoHandler
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.KotlinSmartStepTarget
import org.jetbrains.kotlin.idea.debugger.test.util.KotlinOutputChecker
import org.jetbrains.kotlin.idea.debugger.test.util.SteppingInstruction
import org.jetbrains.kotlin.idea.debugger.test.util.SteppingInstructionKind
import org.jetbrains.kotlin.idea.debugger.test.util.render
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils.isIgnoredTarget
import org.jetbrains.kotlin.idea.test.KotlinBaseTest
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets

abstract class KotlinDescriptorTestCaseWithStepping : KotlinDescriptorTestCase() {
    companion object {
        //language=RegExp
        const val MAVEN_DEPENDENCY_REGEX = """maven\(([a-zA-Z0-9_\-.]+):([a-zA-Z0-9_\-.]+):([a-zA-Z0-9_\-.]+)\)"""
    }

    private val dp: DebugProcessImpl
        get() = debugProcess ?: throw AssertionError("createLocalProcess() should be called before getDebugProcess()")

    @Volatile
    private var myEvaluationContext: EvaluationContextImpl? = null
    val evaluationContext get() = myEvaluationContext!!

    @Volatile
    private var myDebuggerContext: DebuggerContextImpl? = null
    protected open val debuggerContext get() = myDebuggerContext!!

    @Volatile
    private var myCommandProvider: KotlinSteppingCommandProvider? = null
    private val commandProvider get() = myCommandProvider!!

    private val classPath = mutableListOf<String>()

    private val thrownExceptions = mutableListOf<Throwable>()

    private fun initContexts(suspendContext: SuspendContextImpl) {
        myEvaluationContext = createEvaluationContext(suspendContext)
        myDebuggerContext = createDebuggerContext(suspendContext)
        myCommandProvider = JvmSteppingCommandProvider.EP_NAME.extensions.firstIsInstance<KotlinSteppingCommandProvider>()
    }

    private fun SuspendContextImpl.getKotlinStackFrames(): List<KotlinStackFrame> {
        if (myInProgress) {
            val proxy = frameProxy ?: return emptyList()
            return KotlinPositionManager(debugProcess)
              .createStackFrames(StackFrameDescriptorImpl(proxy, MethodsTracker()))
              .filterIsInstance<KotlinStackFrame>()
        }
        return emptyList()
    }

    override fun createEvaluationContext(suspendContext: SuspendContextImpl): EvaluationContextImpl? {
        return try {
            val proxy = suspendContext.getKotlinStackFrames().firstOrNull()?.stackFrameProxy ?: suspendContext.frameProxy
            assertNotNull(proxy)
            EvaluationContextImpl(suspendContext, proxy, proxy?.thisObject())
        } catch (e: EvaluateException) {
            error(e)
            null
        }
    }

    internal fun process(instructions: List<SteppingInstruction>) {
        instructions.forEach(this::process)
    }

    internal fun doOnBreakpoint(action: SuspendContextImpl.() -> Unit) {
        super.onBreakpoint {
            try {
                initContexts(it)
                it.printContext()
                it.action()
            } catch (e: AssertionError) {
                throw e
            } catch (e: Throwable) {
                e.printStackTrace()
                resume(it)
            }
        }
    }

    internal fun finish() {
        doOnBreakpoint {
            resume(this)
        }
    }

    private fun SuspendContextImpl.doStepInto(ignoreFilters: Boolean, smartStepFilter: MethodFilter?) {
        val stepIntoCommand =
            runReadAction { commandProvider.getStepIntoCommand(this, ignoreFilters, smartStepFilter) }
                ?: dp.createStepIntoCommand(this, ignoreFilters, smartStepFilter)

        dp.managerThread.schedule(stepIntoCommand)
    }

    private fun SuspendContextImpl.doStepOut() {
        val stepOutCommand = runReadAction { commandProvider.getStepOutCommand(this, debuggerContext) }
            ?: dp.createStepOutCommand(this)

        dp.managerThread.schedule(stepOutCommand)
    }

    override fun setUp() {
        super.setUp()
        atDebuggerTearDown { classPath.clear() }
    }

    private fun SuspendContextImpl.doStepOver(ignoreBreakpoints: Boolean = false) {
        val stepOverCommand = runReadAction {
            val sourcePosition = debuggerContext.sourcePosition
            commandProvider.getStepOverCommand(this, ignoreBreakpoints, sourcePosition)
        } ?: dp.createStepOverCommand(this, ignoreBreakpoints)

        dp.managerThread.schedule(stepOverCommand)
    }

    private fun process(instruction: SteppingInstruction) {
        fun loop(count: Int, block: SuspendContextImpl.() -> Unit) {
            repeat(count) {
                doOnBreakpoint(block)
            }
        }

        when (instruction.kind) {
            SteppingInstructionKind.StepInto -> loop(instruction.arg) { doStepInto(false, null) }
            SteppingInstructionKind.StepIntoIgnoreFilters -> loop(instruction.arg) { doStepInto(true, null) }
            SteppingInstructionKind.StepOut -> loop(instruction.arg) { doStepOut() }
            SteppingInstructionKind.StepOver -> loop(instruction.arg) { doStepOver() }
            SteppingInstructionKind.ForceStepOver -> loop(instruction.arg) { doStepOver(ignoreBreakpoints = true) }
            SteppingInstructionKind.SmartStepInto -> loop(instruction.arg) { doSmartStepInto() }
            SteppingInstructionKind.SmartStepIntoByIndex -> doOnBreakpoint { doSmartStepInto(instruction.arg) }
            SteppingInstructionKind.Resume -> loop(instruction.arg) { resume(this) }
            SteppingInstructionKind.SmartStepTargetsExpectedNumber ->
                doOnBreakpoint {
                    checkNumberOfSmartStepTargets(instruction.arg)
                    resume(this)
                }
        }
    }

    private fun checkNumberOfSmartStepTargets(expectedNumber: Int) {
        val smartStepFilters = createSmartStepIntoFilters()
        try {
            assertEquals(
                "Actual and expected numbers of smart step targets do not match",
                expectedNumber,
                smartStepFilters.size
            )
        } catch (ex: AssertionFailedError) {
            thrownExceptions.add(ex)
        }
    }

    private fun SuspendContextImpl.doSmartStepInto(chooseFromList: Int = 0) {
        this.doSmartStepInto(chooseFromList, false)
    }

    override fun throwExceptionsIfAny() {
        if (thrownExceptions.isNotEmpty()) {
            if (!isTestIgnored()) {
                throw AssertionError(
                    "Test failed with exceptions:\n${thrownExceptions.renderStackTraces()}"
                )
            } else {
                (checker as? KotlinOutputChecker)?.threwException = true
            }
        }
    }

    private fun List<Throwable>.renderStackTraces(): String {
        val outputStream = ByteArrayOutputStream()
        PrintStream(outputStream, true, StandardCharsets.UTF_8).use {
            for (throwable in this) {
                throwable.printStackTrace(it)
            }
        }
        return outputStream.toString(StandardCharsets.UTF_8)
    }

    protected fun isTestIgnored(): Boolean {
        val outputFile = getExpectedOutputFile()
        return outputFile.exists() && isIgnoredTarget(targetBackend(), outputFile)
    }

    private fun SuspendContextImpl.printContext() {
        runReadAction {
            if (this.frameProxy == null) {
                return@runReadAction println("Context thread is null", ProcessOutputTypes.SYSTEM)
            }

            val sourcePosition = PositionUtil.getSourcePosition(this)
            println(sourcePosition?.render() ?: "null", ProcessOutputTypes.SYSTEM)
            extraPrintContext(this)
        }
    }

    protected open fun extraPrintContext(context: SuspendContextImpl) {}

    private fun SuspendContextImpl.doSmartStepInto(chooseFromList: Int, ignoreFilters: Boolean) {
        val filters = createSmartStepIntoFilters()
        if (chooseFromList == 0) {
            filters.forEach {
                doStepInto(ignoreFilters, it)
            }
        } else {
            try {
                doStepInto(ignoreFilters, filters[chooseFromList - 1])
            } catch (e: IndexOutOfBoundsException) {
                val elementText = runReadAction {
                    val elementAt = debuggerContext.sourcePosition.elementAt ?: return@runReadAction "<no-element>"
                    elementAt.getElementTextWithContext()
                }
                throw AssertionError("Couldn't find smart step into command at: \n$elementText", e)
            }
        }
    }

    private fun createSmartStepIntoFilters(): List<MethodFilter> {
        val position = debuggerContext.sourcePosition
        val stepTargets = KotlinSmartStepIntoHandler()
            .findStepIntoTargets(position, debuggerSession)
            .blockingGet(XDebuggerTestUtil.TIMEOUT_MS)
            ?: error("Couldn't calculate smart step targets")

        // the resulting order is different from the order in code when stepping some methods are filtered
        // due to de-prioritisation in JvmSmartStepIntoHandler.reorderWithSteppingFilters
        if (stepTargets.none { DebugProcessImpl.isClassFiltered(it.className)}) {
            try {
                TestCase.assertEquals("Smart step targets are not sorted by position in tree",
                                      stepTargets.sortedByPositionInTree().map { runReadAction { it.presentation } },
                                      stepTargets.map { runReadAction { it.presentation } })
            } catch (e: AssertionFailedError) {
                thrownExceptions.add(e)
            }
        }
        return runReadAction {
            stepTargets.mapNotNull { stepTarget ->
                when (stepTarget) {
                    is KotlinSmartStepTarget -> stepTarget.createMethodFilter()
                    is MethodSmartStepTarget -> BasicStepMethodFilter(stepTarget.method, stepTarget.getCallingExpressionLines())
                    else -> null
                }
            }
        }
    }

    private fun List<SmartStepTarget>.sortedByPositionInTree(): List<SmartStepTarget> {
        if (isEmpty()) return emptyList()
        val sorted = mutableListOf<SmartStepTarget>()
        runReadAction {
            val elementAt = debuggerContext.sourcePosition.elementAt ?: error("Can not sort smart targets source position element is not defined")
            val searchEntryPoint = elementAt.getContainingMethod() ?: error("Can not sort smart targets as cannot find the containing element")
            searchEntryPoint.accept(object : KtTreeVisitorVoid() {
                override fun visitElement(element: PsiElement) {
                    val target = find { it.highlightElement === element }
                    if (target != null) {
                        sorted.add(target)
                    }
                    super.visitElement(element)
                }
            })
        }
        assert(sorted.size == size) { "Tree visitor was supposed to find all $size smart targets, but only ${sorted.size} found" }
        return sorted
    }

    protected fun SuspendContextImpl.runActionInSuspendCommand(action: SuspendContextImpl.() -> Unit) {
        if (myInProgress) {
            action()
        } else {
            val command = object : SuspendContextCommandImpl(this) {
                override fun contextAction(suspendContext: SuspendContextImpl) {
                    action(suspendContext)
                }
            }

            // Try to execute the action inside a command if we aren't already inside it.
            debuggerSession.process.managerThread.invoke(command)
        }
    }

    protected fun processStackFramesOnPooledThread(callback: List<XStackFrame>.() -> Unit) {
        val frameProxy = debuggerContext.frameProxy ?: error("Frame proxy is absent")
        val debugProcess = debuggerContext.debugProcess ?: error("Debug process is absent")
        val nodeManager = debugProcess.xdebugProcess!!.nodeManager
        val descriptor = nodeManager.getStackFrameDescriptor(null, frameProxy)
        val stackFrames = debugProcess.positionManager.createStackFrames(descriptor)
        if (stackFrames.isEmpty()) {
            error("Can't create stack frame for $descriptor")
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            stackFrames.callback()
        }
    }

    protected fun countBreakpointsNumber(file: KotlinBaseTest.TestFile) =
        InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.content, "//Breakpoint!").size

    protected fun SuspendContextImpl.invokeInManagerThread(callback: () -> Unit) {
        assert(debugProcess.isAttached)
        debugProcess.managerThread.invokeCommand(object : DebuggerCommand {
            override fun action() = callback()
            override fun commandCancelled() = error(message = "Test was cancelled")
        })
    }

    override fun addMavenDependency(compilerFacility: DebuggerTestCompilerFacility, library: String) {
        addMavenDependency(compilerFacility, library, module)
    }

    fun addMavenDependency(compilerFacility: DebuggerTestCompilerFacility, library: String, module: Module) {
        val regex = Regex(MAVEN_DEPENDENCY_REGEX)
        val result = regex.matchEntire(library) ?: return
        val (_, groupId: String, artifactId: String, version: String) = result.groupValues
        addMavenDependency(compilerFacility, groupId, artifactId, version, module)
    }

    override fun createJavaParameters(mainClass: String?): JavaParameters {
        val params = super.createJavaParameters(mainClass)
        for (entry in classPath) {
            params.classPath.add(entry)
        }
        return params
    }

    protected fun addMavenDependency(
        compilerFacility: DebuggerTestCompilerFacility,
        groupId: String, artifactId: String,
        version: String,
        module: Module
    ) {
        val description = JpsMavenRepositoryLibraryDescriptor(groupId, artifactId, version)
        val artifacts = loadDependencies(description)
        compilerFacility.addDependencies(artifacts.map { it.file.presentableUrl })
        addLibraries(artifacts, module)
    }

    private fun addLibraries(artifacts: MutableList<OrderRoot>, module: Module) {
        runInEdtAndWait {
            ConfigLibraryUtil.addLibrary(module, "ARTIFACTS") {
                for (artifact in artifacts) {
                    classPath.add(artifact.file.presentableUrl) // for sandbox jvm
                    addRoot(artifact.file, artifact.type)
                }
            }
        }
    }

    protected fun loadDependencies(
        description: JpsMavenRepositoryLibraryDescriptor
    ): MutableList<OrderRoot> {
        return JarRepositoryManager.loadDependenciesSync(
            project, description, setOf(ArtifactKind.ARTIFACT),
            RemoteRepositoryDescription.DEFAULT_REPOSITORIES, null
        ) ?: throw AssertionError("Maven Dependency not found: $description")
    }
}
