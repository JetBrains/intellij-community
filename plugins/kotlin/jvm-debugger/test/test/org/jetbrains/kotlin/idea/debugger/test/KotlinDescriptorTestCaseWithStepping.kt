// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.actions.MethodSmartStepTarget
import com.intellij.debugger.actions.SmartStepTarget
import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.JvmSteppingCommandProvider
import com.intellij.debugger.impl.PositionUtil
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XSourcePositionImpl
import junit.framework.AssertionFailedError
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils.areLogErrorsIgnored
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils.isIgnoredTarget
import org.jetbrains.kotlin.idea.debugger.KotlinPositionManager
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.InlineStackTraceCalculator
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.KotlinStackFrame
import org.jetbrains.kotlin.idea.debugger.core.stepping.KotlinSteppingCommandProvider
import org.jetbrains.kotlin.idea.debugger.getContainingMethod
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.KotlinSmartStepIntoHandler
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.KotlinSmartStepTarget
import org.jetbrains.kotlin.idea.debugger.test.util.*
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinBaseTest
import org.jetbrains.kotlin.idea.test.allKotlinFiles
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.absolutePathString

abstract class KotlinDescriptorTestCaseWithStepping : KotlinDescriptorTestCase() {
    companion object {
        //language=RegExp
        const val MAVEN_DEPENDENCY_REGEX = """maven\(([a-zA-Z0-9_\-.]+):([a-zA-Z0-9_\-.]+):([a-zA-Z0-9_\-.]+)\)"""
        const val BAZEL_DEPENDENCY_LABEL_REGEX = """(classes|sources)\((@[a-zA-Z0-9_\-]+//([a-z0-9_\-]+)?:[a-z0-9._\-]+)\)"""
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

    protected val agentListJpsDesc = mutableListOf<JpsMavenRepositoryLibraryDescriptor>()
    protected val agentList = mutableListOf<BazelDependencyLabelDescriptor>()

    private fun initContexts(suspendContext: SuspendContextImpl) {
        myEvaluationContext = createEvaluationContext(suspendContext)
        myDebuggerContext = createDebuggerContext(suspendContext)
        myCommandProvider = JvmSteppingCommandProvider.EP_NAME.extensions.firstIsInstance<KotlinSteppingCommandProvider>()
    }

    private fun SuspendContextImpl.getFirstFrame(): KotlinStackFrame? {
        val frameProxy = getFrameProxy(this) ?: return null
        val descriptor = StackFrameDescriptorImpl(frameProxy)
        val frames = if (myInProgress) {
            KotlinPositionManager(debugProcess).createStackFrames(descriptor)
        } else {
            InlineStackTraceCalculator.calculateInlineStackTrace(descriptor)
        }

        return frames?.firstOrNull() as? KotlinStackFrame
    }

    override fun createEvaluationContext(suspendContext: SuspendContextImpl): EvaluationContextImpl? {
        return try {
            val firstFrame = suspendContext.getFirstFrame()
            val proxy = firstFrame?.stackFrameProxy ?: suspendContext.frameProxy
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

    protected open fun doOnBreakpoint(action: SuspendContextImpl.() -> Unit) {
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

        managerThread.schedule(stepIntoCommand)
    }

    private fun SuspendContextImpl.doStepOut() {
        val stepOutCommand = runReadAction { commandProvider.getStepOutCommand(this, debuggerContext) }
            ?: dp.createStepOutCommand(this)

        managerThread.schedule(stepOutCommand)
    }

    private fun SuspendContextImpl.doRunToCursor(lineIndex: Int, fileName: String) {
        val runToCursorCommand = runReadAction {
            val allKotlinFiles = project.allKotlinFiles()
            val ktFile = allKotlinFiles.singleOrNull { it.name == fileName } ?: error("No file with name $fileName")
            val virtualFile = ktFile.virtualFile
            val xSourcePosition = XSourcePositionImpl.create(virtualFile, lineIndex + 1) // need next line
            commandProvider.getRunToCursorCommand(this, xSourcePosition, false) ?: dp.createRunToCursorCommand(this, xSourcePosition, false)
        }

        managerThread.schedule(runToCursorCommand)
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

        managerThread.schedule(stepOverCommand)
    }

    private fun process(instruction: SteppingInstruction) {
        fun loop(count: Int, block: SuspendContextImpl.() -> Unit) {
            repeat(count) {
                doOnBreakpoint(block)
            }
        }

        when (instruction.kind) {
            SteppingInstructionKind.RunToCursor -> doOnBreakpoint { doRunToCursor(instruction.lineIndex, instruction.fileName) }
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
            val actualTargets = smartStepFilters.joinToString(prefix = "[", postfix = "]") {
                if (it is NamedMethodFilter) it.methodName else it.toString()
            }
            val location = debuggerContext.suspendContext?.location
            assertEquals(
                "Actual and expected numbers of smart step targets do not match, targets: $actualTargets location: $location",
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

    fun isTestIgnored(): Boolean {
        val outputFile = getExpectedOutputFile()
        return outputFile.exists() && isIgnoredTarget(TargetBackend.JVM_IR_WITH_IR_EVALUATOR, outputFile)
    }

    override fun areLogErrorsIgnored(): Boolean {
        return isTestIgnored() || areLogErrorsIgnored(dataFile())
    }

    private fun SuspendContextImpl.printContext() {
        runReadAction {
            if (this.frameProxy == null) {
                return@runReadAction println("Context thread is null", ProcessOutputTypes.SYSTEM)
            }

            println(PositionUtil.getSourcePosition(this)?.render() ?: "null", ProcessOutputTypes.SYSTEM)
            extraPrintContext(this)
        }
    }

    protected open fun extraPrintContext(context: SuspendContextImpl) {}

    private fun SuspendContextImpl.doSmartStepInto(chooseFromList: Int, ignoreFilters: Boolean) {
        val filters = createSmartStepIntoFilters()
        if (chooseFromList == 0) {
            if (filters.isEmpty()) {
                throw AssertionError("Couldn't find any smart step into targets at: \n${getElementText()}")
            }
            filters.forEach {
                doStepInto(ignoreFilters, it)
            }
        } else {
            try {
                doStepInto(ignoreFilters, filters[chooseFromList - 1])
            } catch (e: IndexOutOfBoundsException) {
                throw AssertionError("Couldn't find smart step into command at: \n${getElementText()}", e)
            }
        }
    }

    private fun getElementText() = runReadAction {
        val elementAt = debuggerContext.sourcePosition.elementAt ?: return@runReadAction "<no-element>"
        elementAt.getElementTextWithContext()
    }

    private fun createSmartStepIntoFilters(): List<MethodFilter> {
        val stepTargets = KotlinSmartStepIntoHandler()
            .findSmartStepTargetsSync(debuggerContext.sourcePosition, debuggerSession)

        // the resulting order is different from the order in code when stepping some methods are filtered
        // due to de-prioritisation in JvmSmartStepIntoHandler.reorderWithSteppingFilters
        if (runReadAction { stepTargets.none { DebugProcessImpl.isClassFiltered(it.className)} }) {
            try {
                assertEquals("Smart step targets are not sorted by position in tree",
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
            val command = object : SuspendContextCommandImpl(this@runActionInSuspendCommand) {
                override fun contextAction(suspendContext: SuspendContextImpl) {
                    action(suspendContext)
                }
            }

            // Try to execute the action inside a command if we aren't already inside it.
            managerThread.invoke(command)
        }
    }

    protected fun processStackFramesOnPooledThread(callback: List<XStackFrame>.() -> Unit) {
        val frameProxy = debuggerContext.frameProxy ?: error("Frame proxy is absent")
        val debugProcess = debuggerContext.debugProcess ?: error("Debug process is absent")
        val nodeManager = debugProcess.xdebugProcess!!.nodeManager
        val descriptor = nodeManager.getStackFrameDescriptor(null, frameProxy)
        val stackFrames = debugProcess.positionManager.createStackFrames(descriptor)
        if (stackFrames.isNullOrEmpty()) {
            error("Can't create stack frame for $descriptor")
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            stackFrames.callback()
        }
    }

    protected fun countBreakpointsNumber(file: KotlinBaseTest.TestFile) =
        InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.content, "//Breakpoint!").size

    @Deprecated("Use org.jetbrains.kotlin.idea.debugger.test.KotlinDescriptorTestCase.addLabelDependency instead")
    override fun addMavenDependency(compilerFacility: DebuggerTestCompilerFacility, library: String) {
        addMavenDependency(compilerFacility, library, module)
        processAgentDependencies(library, compilerFacility)
    }

    override fun addLibraryByLabelDependency(compilerFacility: DebuggerTestCompilerFacility, library: String) {
        addBazelLabelDependency(compilerFacility, library, module)
    }

    override fun addJavaAgentByLabelDependency(compilerFacility: DebuggerTestCompilerFacility, library: String) {
        processLabelAgentDependencies(library, compilerFacility)
    }

    private fun addMavenDependency(compilerFacility: DebuggerTestCompilerFacility, library: String, module: Module) {
        val regex = Regex(MAVEN_DEPENDENCY_REGEX)
        val result = regex.matchEntire(library) ?: return
        val (_, groupId: String, artifactId: String, version: String) = result.groupValues
        addMavenDependency(compilerFacility, groupId, artifactId, version, module)
    }

    private fun addBazelLabelDependency(compilerFacility: DebuggerTestCompilerFacility, library: String, module: Module) {
        val bazelLabelDescriptor = BazelDependencyLabelDescriptor.fromString(library)
        addLabelDependency(compilerFacility, bazelLabelDescriptor, module)
    }

    private fun processAgentDependencies(library: String, compilerFacility: DebuggerTestCompilerFacility) {
        val regex = Regex(pattern = "$MAVEN_DEPENDENCY_REGEX(-javaagent)?")
        val result = regex.matchEntire(library) ?: return
        val (_, groupId: String, artifactId: String, version: String, agent: String) = result.groupValues
        if ("-javaagent" == agent)
            agentListJpsDesc.add(JpsMavenRepositoryLibraryDescriptor(groupId, artifactId, version, false))
        addMavenDependency(compilerFacility, groupId, artifactId, version, module)
    }

    private fun processLabelAgentDependencies(library: String, compilerFacility: DebuggerTestCompilerFacility) {
        val bazelLabelDescriptor = BazelDependencyLabelDescriptor.fromString(library)
        agentList.add(bazelLabelDescriptor)
        addLabelDependency(compilerFacility, bazelLabelDescriptor, module)
    }

    override fun createJavaParameters(mainClass: String?): JavaParameters {
        val params = super.createJavaParameters(mainClass)
        for (entry in classPath) {
            params.classPath.add(entry)
        }

        if (agentList.isNotEmpty() && agentListJpsDesc.isNotEmpty()) {
            // temporary, attach javaagents only by new notation // ATTACH_JAVA_AGENT_BY_LABEL:
            error(
                "Attach the agent using exactly one directive: either \"// ATTACH_LIBRARY:\" " +
                "or \"// ATTACH_JAVA_AGENT_BY_LABEL:\". Do not use both."
            )
        }

        // temporary as well
        for (agent in agentListJpsDesc) {
            val dependencies = loadDependencies(agent)
            for (dependency in dependencies) {
                if (dependency.type == OrderRootType.CLASSES) {
                    params.vmParametersList.add("-javaagent:${dependency.file.presentableUrl}")
                }
            }
        }
        for (dependencyDescriptor in agentList) {
            val dependency = loadDependency(dependencyDescriptor)
            if (dependency.type == OrderRootType.CLASSES) {
                params.vmParametersList.add("-javaagent:${dependency.file.presentableUrl}")
            }
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

    protected fun addLabelDependency(
        compilerFacility: DebuggerTestCompilerFacility,
        bazelLabelDescriptor: BazelDependencyLabelDescriptor,
        module: Module
    ) {
        val artifact = loadDependency(bazelLabelDescriptor)
        compilerFacility.addDependencies(listOf(artifact.file.presentableUrl) )
        addLibraries(mutableListOf(artifact), module)
    }

    protected fun addLibraries(compilerFacility: DebuggerTestCompilerFacility, libraries: List<Path>) {
        compilerFacility.addDependencies(libraries.map { it.absolutePathString() })
        runInEdtAndWait {
            ConfigLibraryUtil.addLibrary(module, "ARTIFACTS") {
                libraries.forEach { library ->
                    classPath.add(library.absolutePathString()) // for sandbox jvm
                    addRoot(library.absolutePathString(), OrderRootType.CLASSES)
                }
            }
        }

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


    protected open fun jarRepositories() : List<RemoteRepositoryDescription> {
        return RemoteRepositoryDescription.DEFAULT_REPOSITORIES
    }

    protected fun loadDependency(
        bazelLabelDescriptor: BazelDependencyLabelDescriptor
    ): OrderRoot {
        val libFile = TestKotlinArtifacts.getKotlinDepsByLabel(bazelLabelDescriptor.label)

        val manager = VirtualFileManager.getInstance()
        val url: String = VfsUtil.getUrlForLibraryRoot(libFile)
        val file = manager.refreshAndFindFileByUrl(url) ?: error("Cannot find $url")

        val orderRootType = when (bazelLabelDescriptor.type) {
            BazelDependencyLabelDescriptor.Companion.Type.SOURCES -> OrderRootType.SOURCES
            BazelDependencyLabelDescriptor.Companion.Type.CLASSES -> OrderRootType.CLASSES
        }
        return OrderRoot(file, orderRootType)
    }

    protected fun loadDependencies(
        description: JpsMavenRepositoryLibraryDescriptor
    ): MutableList<OrderRoot> {

        return JarRepositoryManager.loadDependenciesSync(
            project, description, setOf(ArtifactKind.ARTIFACT, ArtifactKind.SOURCES),
            jarRepositories(), null
        ) ?: throw AssertionError("Maven Dependency not found: $description")
    }

    /*
     * Prints stack traces with variables on a current breakpoint.
     *
     * Internally the function computes children of `com.intellij.debugger.engine.JavaStackFrame`,
     * which is a non-blocking operation that is scheduled as a separate action on the debugger thread.
     * If a debug process is resumed before the children of a stack frame are computed, an exception will
     * occur and nothing will be printed.
     */
    protected fun printFrame(suspendContext: SuspendContextImpl, completion: SuspendContextImpl.() -> Unit) {
        processStackFramesOnPooledThread {
            for (stackFrame in this) {
                val result = FramePrinter(suspendContext).print(stackFrame)
                print(result, ProcessOutputTypes.SYSTEM)
            }
            assert(debugProcess.isAttached)
            suspendContext.managerThread.schedule(object : SuspendContextCommandImpl(suspendContext) {
                override fun contextAction(suspendContext: SuspendContextImpl) {
                    completion(suspendContext)
                }
                override fun commandCancelled() = error(message = "Test was cancelled")
            })
        }
    }

    protected data class BazelDependencyLabelDescriptor(val type: Type, val label: String) {
        companion object {
            fun fromString(text: String): BazelDependencyLabelDescriptor {
                val regex = Regex(pattern = BAZEL_DEPENDENCY_LABEL_REGEX)
                val result = regex.matchEntire(text) ?: error("Unable to parse '$text' in // ATTACH_LABEL: specification")
                val (_, type: String, label: String) = result.groupValues
                return BazelDependencyLabelDescriptor(Type.valueOf(type.uppercase()), label)
            }

            enum class Type {
                CLASSES,
                SOURCES;
            }
        }
    }
}
