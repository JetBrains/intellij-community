// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.CompilerReferenceService
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase
import com.intellij.compiler.backwardRefs.DirtyScopeHolder
import com.intellij.compiler.server.BuildManager
import com.intellij.compiler.server.BuildManagerListener
import com.intellij.compiler.server.CustomBuilderMessageHandler
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.LibraryScopeCache
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.Processor
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchInheritors
import org.jetbrains.kotlin.idea.search.not
import org.jetbrains.kotlin.idea.search.restrictToKotlinSources
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.incremental.LookupStorage
import org.jetbrains.kotlin.incremental.LookupSymbol
import org.jetbrains.kotlin.incremental.storage.RelativeFileToPathConverter
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.io.File
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.LongAdder
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * Based on [com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase] and [com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl]
 */
@Service(Service.Level.PROJECT)
class KotlinCompilerReferenceIndexService(val project: Project) : Disposable, ModificationTracker {
    private var storage: LookupStorage? = null
    private var activeBuildCount = 0
    private val compilationCounter = LongAdder()
    private val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
    private val supportedFileTypes: Set<FileType> = setOf(KotlinFileType.INSTANCE, JavaFileType.INSTANCE)
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
                if (builderId == SettingConstants.KOTLIN_COMPILER_REFERENCE_INDEX_BUILDER_ID) {
                    mutableSet += messageText
                }
            },
        )
    }

    private val lock = ReentrantReadWriteLock()
    private fun <T> withWriteLock(action: () -> T): T = lock.write(action)
    private fun <T> withReadLock(action: () -> T): T = lock.read(action)
    private fun <T> tryWithReadLock(action: () -> T): T? = lock.readLock().run {
        if (tryLock())
            try {
                action()
            } finally {
                unlock()
            }
        else
            null
    }

    private fun withDirtyScopeUnderWriteLock(updater: DirtyScopeHolder.() -> Unit): Unit = withWriteLock { dirtyScopeHolder.updater() }
    private fun <T> withDirtyScopeUnderReadLock(readAction: DirtyScopeHolder.() -> T): T = withReadLock { dirtyScopeHolder.readAction() }

    init {
        dirtyScopeHolder.installVFSListener(this)

        val compilerManager = CompilerManager.getInstance(project)
        val isUpToDate = compilerManager.takeIf { kotlinDataContainer != null }
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

            override fun buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean) {
                if (project === this@KotlinCompilerReferenceIndexService.project) {
                    executeOnBuildThread {
                        if (!runReadAction { this@KotlinCompilerReferenceIndexService.project.isDisposed }) {
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

    private val kotlinDataContainer: Path?
        get() = BuildDataPathsImpl(BuildManager.getInstance().getProjectSystemDirectory(project)).targetsDataRoot
            .toPath()
            .resolve(SettingConstants.KOTLIN_DATA_CONTAINER_ID)
            .takeIf { it.exists() && it.isDirectory() }
            ?.listDirectoryEntries("${SettingConstants.KOTLIN_DATA_CONTAINER_ID}*")
            ?.firstOrNull()

    private fun openStorage() {
        val basePath = project.basePath ?: return
        val pathConverter = RelativeFileToPathConverter(File(basePath))
        val targetDataDir = kotlinDataContainer?.toFile() ?: run {
            LOG.warn("try to open storage without index directory")
            return
        }

        storage = LookupStorage(targetDataDir, pathConverter)
        LOG.info("kotlin compiler references index is opened")
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
                buildScopeWithReferences(referentFiles(element), element),
                PsiModificationTracker.MODIFICATION_COUNT,
                this,
            )
        }
    }

    @TestOnly
    fun findReferenceFilesInTests(element: PsiElement): Set<VirtualFile>? = referentFiles(element)

    private fun referentFiles(element: PsiElement): Set<VirtualFile>? = tryWithReadLock(fun(): Set<VirtualFile>? {
        val storage = storage ?: return null
        val fqName = when (element) {
            is KtClassOrObject, is PsiClass -> element.getKotlinFqName()
            else -> null
        } ?: return null

        val virtualFile = PsiUtilCore.getVirtualFile(element) ?: return null
        if (projectFileIndex.isInSource(virtualFile) && virtualFile in dirtyScopeHolder) return null
        val fqNames: List<FqName> = listOf(fqName) + if (projectFileIndex.isInLibrary(virtualFile)) {
            computeInLibraryScope { findHierarchyInLibrary(element) }
        } else {
            emptyList()
        }

        return fqNames.flatMapTo(mutableSetOf()) { currentFqName ->
            val name = currentFqName.shortName().asString()
            val scope = currentFqName.parent().takeUnless(FqName::isRoot)?.asString() ?: ""
            storage.get(LookupSymbol(name, scope)).mapNotNull { VfsUtil.findFile(Path(it), true) }
        }
    })

    private val isInsideLibraryScopeThreadLocal = ThreadLocal.withInitial { false }
    private fun isInsideLibraryScope(): Boolean = CompilerReferenceService.getInstanceIfEnabled(project)
        ?.safeAs<CompilerReferenceServiceBase<*>>()
        ?.isInsideLibraryScope
        ?: isInsideLibraryScopeThreadLocal.get()

    private fun <T> computeInLibraryScope(action: () -> T): T = CompilerReferenceService.getInstanceIfEnabled(project)
        ?.safeAs<CompilerReferenceServiceBase<*>>()
        ?.computeInLibraryScope<T, Throwable>(action)
        ?: run {
            isInsideLibraryScopeThreadLocal.set(true)
            try {
                action()
            } finally {
                isInsideLibraryScopeThreadLocal.set(false)
            }
        }

    private fun findHierarchyInLibrary(basePsiElement: PsiElement): List<FqName> {
        val overridden = mutableListOf<FqName>()
        val processor = Processor { clazz: PsiClass ->
            clazz.takeUnless { it.hasModifierProperty(PsiModifier.PRIVATE) }
                ?.let { runReadAction { it.qualifiedName } }
                ?.let { overridden += FqName(it) }

            true
        }

        HierarchySearchRequest(
            originalElement = basePsiElement,
            searchScope = LibraryScopeCache.getInstance(project).librariesOnlyScope,
            searchDeeply = true,
        ).searchInheritors().forEach(processor)
        return overridden
    }

    private fun isServiceEnabledFor(element: PsiElement): Boolean = !isInsideLibraryScope() && storage != null && isEnabled &&
            runReadAction { element.containingFile }
                ?.let(InjectedLanguageManager.getInstance(project)::isInjectedFragment)
                ?.not() == true

    private fun buildScopeWithReferences(virtualFiles: Set<VirtualFile>?, element: PsiElement): GlobalSearchScope? {
        if (virtualFiles == null) return null

        // knows everything
        val referencesScope = GlobalSearchScope.filesWithoutLibrariesScope(project, virtualFiles)

        /***
         * can contain all languages, but depends on [supportedFileTypes]
         * [com.intellij.compiler.backwardRefs.DirtyScopeHolder.getModuleForSourceContentFile]
         */
        val knownDirtyScope = withDirtyScopeUnderReadLock { dirtyScope }

        // [supportedFileTypes] without references + can contain references from other languages
        val wholeClearScope = knownDirtyScope.not()

        // [supportedFileTypes] without references
        //val knownCleanScope = GlobalSearchScope.getScopeRestrictedByFileTypes(wholeClearScope, *supportedFileTypes.toTypedArray())
        val knownCleanScope = wholeClearScope.restrictToKotlinSources()

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
         *   [knownCleanScope] contains m3[6]
         *   [wholeDirtyScope] contains m1[1, 2, 3], m2[4], m3[5, 7]
         */

        val mayContainReferencesScope = referencesScope.uniteWith(wholeDirtyScope)
        return CompilerReferenceServiceBase.scopeWithLibraryIfNeeded(project, projectFileIndex, mayContainReferencesScope, element)
    }

    override fun dispose(): Unit = withWriteLock { closeStorage() }

    override fun getModificationCount(): Long = compilationCounter.sum()

    companion object {
        operator fun get(project: Project): KotlinCompilerReferenceIndexService = project.service()
        fun getInstanceIfEnable(project: Project): KotlinCompilerReferenceIndexService? = if (isEnabled) get(project) else null
        const val SETTINGS_ID: String = "kotlin.compiler.ref.index"
        val isEnabled: Boolean get() = AdvancedSettings.getBoolean(SETTINGS_ID)
        private val LOG: Logger = logger<KotlinCompilerReferenceIndexService>()
    }

    class InitializationActivity : StartupActivity.DumbAware {
        override fun runActivity(project: Project) {
            getInstanceIfEnable(project)
        }
    }
}

private fun executeOnBuildThread(compilationFinished: () -> Unit): Unit =
    if (isUnitTestMode()) {
        compilationFinished()
    } else {
        BuildManager.getInstance().runCommand(compilationFinished)
    }
