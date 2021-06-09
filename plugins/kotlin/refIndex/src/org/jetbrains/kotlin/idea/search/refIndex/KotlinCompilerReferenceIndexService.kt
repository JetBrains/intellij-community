// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.backwardRefs.DirtyScopeHolder
import com.intellij.compiler.server.BuildManager
import com.intellij.compiler.server.BuildManagerListener
import com.intellij.compiler.server.CustomBuilderMessageHandler
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.compiler.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.search.not
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.incremental.LookupStorage
import org.jetbrains.kotlin.incremental.storage.RelativeFileToPathConverter
import org.jetbrains.kotlin.jps.incremental.KotlinCompilerReferenceIndexBuilder
import org.jetbrains.kotlin.jps.incremental.KotlinDataContainerTarget
import java.io.File
import java.util.*
import java.util.concurrent.atomic.LongAdder
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Based on [com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase] and [com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl]
 */
@Service(Service.Level.PROJECT)
class KotlinCompilerReferenceIndexService(val project: Project) : Disposable, ModificationTracker {
    private var storage: LookupStorage? = null
    private var activeBuildCount = 0
    private val compilationCounter = LongAdder()
    private val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
    private val supportedFileTypes: Set<FileType> = setOf(KotlinFileType.INSTANCE, JavaFileType.INSTANCE, JavaClassFileType.INSTANCE)
    private val dirtyScopeHolder = DirtyScopeHolder(
        project,
        supportedFileTypes,
        projectFileIndex,
        this,
        this,
        FileDocumentManager.getInstance(),
        PsiDocumentManager.getInstance(project),
    ) { connect: MessageBusConnection, mutableSet: MutableSet<String> ->
        connect.subscribe(
            CustomBuilderMessageHandler.TOPIC,
            CustomBuilderMessageHandler { builderId, _, messageText ->
                if (builderId == KotlinCompilerReferenceIndexBuilder.BUILDER_ID) {
                    mutableSet += messageText
                }
            },
        )
    }

    private val lock = ReentrantReadWriteLock()
    private fun <T> withWriteLock(action: () -> T): T = lock.write(action)
    private fun <T> withReadLock(action: () -> T): T = lock.read(action)
    private fun withDirtyScopeUnderWriteLock(updater: DirtyScopeHolder.() -> Unit): Unit = withWriteLock { dirtyScopeHolder.updater() }
    private fun <T> withDirtyScopeUnderReadLock(readAction: DirtyScopeHolder.() -> T): T = withReadLock { dirtyScopeHolder.readAction() }

    init {
        dirtyScopeHolder.installVFSListener(this)

        val compilerManager = CompilerManager.getInstance(project)
        val isUpToDate = compilerManager.takeIf { kotlinDataContainer.exists() }
            ?.createProjectCompileScope(project)
            ?.let(compilerManager::isUpToDate)
            ?: false

        executeOnBuildThread {
            if (isUpToDate) {
                withDirtyScopeUnderWriteLock {
                    upToDateCheckFinished(Module.EMPTY_ARRAY)
                    openStorage()
                }
            } else {
                markAsOutdated()
            }
        }

        subscribeToCompilerEvents()
    }

    private fun subscribeToCompilerEvents() {
        val connection = project.messageBus.connect(this)
        connection.subscribe(BuildManagerListener.TOPIC, object : BuildManagerListener {
            override fun buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean) {
                if (project === this@KotlinCompilerReferenceIndexService.project) {
                    compilationStarted()
                }
            }
        })

        connection.subscribe(CompilerTopics.COMPILATION_STATUS, object : CompilationStatusListener {
            override fun compilationFinished(
                aborted: Boolean,
                errors: Int,
                warnings: Int,
                compileContext: CompileContext,
            ) = compilationFinished(compileContext)

            override fun automakeCompilationFinished(
                errors: Int,
                warnings: Int,
                compileContext: CompileContext,
            ) = compilationFinished(compileContext)

            private fun compilationFinished(context: CompileContext) {
                if (context !is DummyCompileContext && context.project === project) {
                    executeOnBuildThread {
                        if (runReadAction { context.takeUnless { project.isDisposed }?.compileScope?.affectedModules } != null) {
                            compilationFinished()
                        }
                    }
                }
            }
        })
    }

    private fun compilationFinished() {
        val compilerModules = runReadAction {
            project.takeUnless(Project::isDisposed)?.let {
                val manager = ModuleManager.getInstance(it)
                dirtyScopeHolder.compilationAffectedModules.map(manager::findModuleByName)
            }
        }

        withDirtyScopeUnderWriteLock {
            --activeBuildCount
            compilerActivityFinished(compilerModules)
            if (activeBuildCount == 0) openStorage()
            compilationCounter.increment()
        }
    }

    private fun compilationStarted(): Unit = withDirtyScopeUnderWriteLock {
        ++activeBuildCount
        compilerActivityStarted()
        closeStorage()
    }

    private val kotlinDataContainer: File
        get() = BuildDataPathsImpl(
            BuildManager.getInstance().getProjectSystemDirectory(project)
        ).getTargetDataRoot(KotlinDataContainerTarget)

    private fun openStorage() {
        val basePath = project.basePath ?: error("Default project not supported")
        val pathConverter = RelativeFileToPathConverter(File(basePath))
        val targetDataDir = kotlinDataContainer
        storage = LookupStorage(targetDataDir, pathConverter)
    }

    private fun closeStorage() {
        storage?.close()
        storage = null
    }

    private fun markAsOutdated() {
        val modules = runReadAction {
            project.takeUnless(Project::isDisposed)?.let { ModuleManager.getInstance(it).modules }
        } ?: return

        withDirtyScopeUnderWriteLock { upToDateCheckFinished(modules) }
    }

    fun scopeWithCodeReferences(element: PsiElement): GlobalSearchScope? = element.takeIf(this::isServiceEnabledFor)?.let {
        CachedValuesManager.getCachedValue(element) {
            CachedValueProvider.Result.create(
                buildScopeWithReferences(referentFiles(element)),
                PsiModificationTracker.MODIFICATION_COUNT,
                this,
            )
        }
    }

    // TODO
    private fun referentFiles(element: PsiElement): Set<VirtualFile>? = null

    private fun isServiceEnabledFor(element: PsiElement): Boolean = storage != null && isEnabled &&
            runReadAction { element.containingFile }
                ?.let(InjectedLanguageManager.getInstance(project)::isInjectedFragment)
                ?.not() == true

    private fun buildScopeWithReferences(virtualFiles: Set<VirtualFile>?): GlobalSearchScope? {
        if (virtualFiles == null) return null

        // knows everything
        val scopeWithReferences = ScopeWithReferencesOnCompilation(virtualFiles, projectFileIndex)

        /***
         * can contain all languages, but depends on [supportedFileTypes]
         * [com.intellij.compiler.backwardRefs.DirtyScopeHolder.getModuleForSourceContentFile]
         */
        val knownDirtyScope = withDirtyScopeUnderReadLock { dirtyScope }

        // [supportedFileTypes] without references + can contain references from other languages
        val wholeClearScope = knownDirtyScope.not()

        // [supportedFileTypes] without references
        val knownCleanScope = GlobalSearchScope.getScopeRestrictedByFileTypes(wholeClearScope, *supportedFileTypes.toTypedArray())

        // [supportedFileTypes] from dirty scope + other languages from the whole project
        val wholeDirtyScope = knownCleanScope.not()

        /*
         * Example:
         *   module1 (dirty): 1.java, 2.kt, 3.groovy
         *   module2: 4.groovy
         *   module3: 5.java, 6.kt, 7.groovy
         *   -----
         *   [knownDirtyScope] contains m1[1, 2, 3]
         *   [wholeClearScope] contains m2[4], m3[5, 6, 7]
         *   [knownCleanScope] contains m3[5, 6]
         *   [wholeDirtyScope] contains m1[1, 2, 3], m2[4], m3[7]
         */

        return scopeWithReferences.uniteWith(wholeDirtyScope)
    }

    override fun dispose(): Unit = withWriteLock { closeStorage() }

    override fun getModificationCount(): Long = compilationCounter.sum()

    companion object {
        operator fun get(project: Project): KotlinCompilerReferenceIndexService = project.service()
        fun getInstanceIfEnable(project: Project): KotlinCompilerReferenceIndexService? = if (isEnabled) get(project) else null
        val isEnabled: Boolean get() = AdvancedSettings.getBoolean("kotlin.compiler.ref.index")
    }

    class InitializationActivity : StartupActivity.DumbAware {
        override fun runActivity(project: Project) {
            getInstanceIfEnable(project)
        }
    }
}

private class ScopeWithReferencesOnCompilation(
    private val referentFiles: Set<VirtualFile>,
    private val index: ProjectFileIndex,
) : GlobalSearchScope() {
    override fun contains(file: VirtualFile): Boolean = file is VirtualFileWithId && index.isInSourceContent(file) && file in referentFiles
    override fun isSearchInModuleContent(aModule: Module): Boolean = true
    override fun isSearchInLibraries(): Boolean = false
}

private fun executeOnBuildThread(compilationFinished: () -> Unit): Unit =
    if (isUnitTestMode()) {
        compilationFinished()
    } else {
        BuildManager.getInstance().runCommand(compilationFinished)
    }
