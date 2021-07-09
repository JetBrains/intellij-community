// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.CompilerReferenceService
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase
import com.intellij.compiler.backwardRefs.DirtyScopeHolder
import com.intellij.compiler.backwardRefs.LanguageCompilerRefAdapter
import com.intellij.compiler.backwardRefs.SearchId
import com.intellij.compiler.server.BuildManager
import com.intellij.compiler.server.BuildManagerListener
import com.intellij.compiler.server.CustomBuilderMessageHandler
import com.intellij.compiler.server.PortableCachesLoadListener
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.Disposable
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.Processor
import com.intellij.util.containers.generateRecursiveSequence
import com.intellij.util.indexing.StorageException
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.NameEnumerator
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchInheritors
import org.jetbrains.kotlin.idea.search.not
import org.jetbrains.kotlin.idea.search.restrictToKotlinSources
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.java.getPropertyNamesCandidatesByAccessorName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.io.IOException
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
    private var storage: KotlinCompilerReferenceIndexStorage? = null
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

        val isUpToDate = if (KotlinCompilerReferenceIndexStorage.hasIndex(project)) {
            val compilerManager = CompilerManager.getInstance(project)
            compilerManager.createProjectCompileScope(project).let(compilerManager::isUpToDate)
        } else {
            false
        }

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

    private val projectIfNotDisposed: Project?
        get() = project.takeUnless(Project::isDisposed)

    private fun subscribeToCompilerEvents() {
        val connection = projectIfNotDisposed?.messageBus?.connect(this) ?: return
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

        connection.subscribe(PortableCachesLoadListener.TOPIC, object : PortableCachesLoadListener {
            override fun loadingStarted() {
                withWriteLock { closeStorage() }
            }
        })
    }

    private fun compilationFinished() {
        val compilerModules = runReadAction {
            projectIfNotDisposed?.let {
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

    private fun openStorage() {
        storage = KotlinCompilerReferenceIndexStorage.open(project)
    }

    private fun closeStorage() {
        KotlinCompilerReferenceIndexStorage.close(storage)
        storage = null
    }

    private fun markAsOutdated() {
        val modules = runReadAction { projectIfNotDisposed?.let { ModuleManager.getInstance(it).modules } } ?: return
        withDirtyScopeUnderWriteLock { upToDateCheckFinished(modules) }
    }

    private fun <T> runActionSafe(actionName: String, action: () -> T): T? = try {
        action()
    } catch (e: Throwable) {
        if (e is ControlFlowException) throw e

        try {
            LOG.error("an exception during $actionName calculation", e)
        } finally {
            if (e is IOException || e is StorageException) {
                withWriteLock { closeStorage() }
            }
        }

        null
    }

    fun scopeWithCodeReferences(element: PsiElement): GlobalSearchScope? {
        if (!isServiceEnabledFor(element)) return null

        return runActionSafe("scope with code references") {
            CachedValuesManager.getCachedValue(element) {
                CachedValueProvider.Result.create(
                    buildScopeWithReferences(referentFiles(element), element),
                    PsiModificationTracker.MODIFICATION_COUNT,
                    this,
                )
            }
        }
    }

    fun directKotlinSubtypesOf(searchId: SearchId): Collection<FqNameWrapper>? {
        val fqNameWrapper = FqNameWrapper.createFromSearchId(searchId) ?: return null
        return tryWithReadLock { getDirectKotlinSubtypesOf(fqNameWrapper.fqName).toList() }
    }

    @TestOnly
    fun getSubtypesOfInTests(fqName: FqName, deep: Boolean): Sequence<FqName>? = storage?.getSubtypesOf(fqName, deep)

    @TestOnly
    fun getSubtypesOfInTests(hierarchyElement: PsiElement, isFromLibrary: Boolean = false): Sequence<FqName> = getSubtypesOf(
        hierarchyElement,
        isFromLibrary,
    )

    @TestOnly
    fun findReferenceFilesInTests(element: PsiElement): Set<VirtualFile>? = referentFiles(element)

    private fun referentFiles(element: PsiElement): Set<VirtualFile>? = tryWithReadLock(fun(): Set<VirtualFile>? {
        val storage = storage ?: return null
        val originalElement = element.unwrapped ?: return null
        val originalFqNames = extractFqNames(originalElement) ?: return null
        val virtualFile = PsiUtilCore.getVirtualFile(element) ?: return null
        if (projectFileIndex.isInSource(virtualFile) && virtualFile in dirtyScopeHolder) return null

        val isFromLibrary = projectFileIndex.isInLibrary(virtualFile)
        originalFqNames.takeIf { isFromLibrary }?.singleOrNull()?.asString()?.let { fqName ->
            // "Any" is not in the subtypes storage
            if (fqName.startsWith(CommonClassNames.JAVA_LANG_OBJECT) || fqName.startsWith("kotlin.Any")) {
                return null
            }
        }

        val additionalFqNames = findSubclassesFqNamesIfApplicable(originalElement, isFromLibrary)?.let { subclassesFqNames ->
            subclassesFqNames.flatMap { subclass -> originalFqNames.map { subclass.child(it.shortName()) } }
        }.orEmpty()

        return originalFqNames.toSet().plus(additionalFqNames).flatMapTo(mutableSetOf(), storage::getUsages)
    })

    private fun findSubclassesFqNamesIfApplicable(element: PsiElement, isFromLibrary: Boolean): Sequence<FqName>? {
        val hierarchyElement = when (element) {
            is KtClassOrObject -> null
            is PsiMember -> element.containingClass
            is KtCallableDeclaration -> element.containingClassOrObject?.takeUnless { it is KtObjectDeclaration }
            else -> null
        } ?: return null

        return getSubtypesOf(hierarchyElement, isFromLibrary)
    }

    private fun getSubtypesOf(hierarchyElement: PsiElement, isFromLibrary: Boolean): Sequence<FqName> =
        generateRecursiveSequence(computeInitialElementsSequence(hierarchyElement, isFromLibrary)) { fqNameWrapper ->
            val javaValues = getDirectJavaSubtypesOf(fqNameWrapper::asJavaCompilerClassRef)
            val kotlinValues = getDirectKotlinSubtypesOf(fqNameWrapper.fqName)
            kotlinValues + javaValues
        }.map(FqNameWrapper::fqName)

    private fun computeInitialElementsSequence(hierarchyElement: PsiElement, isFromLibrary: Boolean): Sequence<FqNameWrapper> {
        val initialElements = if (isFromLibrary) {
            computeInLibraryScope { findHierarchyInLibrary(hierarchyElement) }
        } else {
            setOf(hierarchyElement)
        }

        val kotlinInitialValues = initialElements.asSequence().mapNotNull(PsiElement::getKotlinFqName).flatMap { fqName ->
            fqName.computeDirectSubtypes(
                withSelf = isFromLibrary,
                selfAction = FqNameWrapper.Companion::createFromFqName,
                subtypesAction = this::getDirectKotlinSubtypesOf
            )
        }

        val javaInitialValues = initialElements.asSequence().flatMap { currentHierarchyElement ->
            currentHierarchyElement.computeDirectSubtypes(
                withSelf = isFromLibrary,
                selfAction = FqNameWrapper.Companion::createFromPsiElement,
                subtypesAction = ::getDirectJavaSubtypesOf
            )
        }

        return kotlinInitialValues + javaInitialValues
    }

    private fun <T> T.computeDirectSubtypes(
        withSelf: Boolean,
        selfAction: (T) -> FqNameWrapper?,
        subtypesAction: (T) -> Sequence<FqNameWrapper>,
    ): Sequence<FqNameWrapper> {
        val directSubtypes = subtypesAction(this)
        return selfAction.takeIf { withSelf }?.invoke(this)?.let { sequenceOf(it) + directSubtypes } ?: directSubtypes
    }

    private fun getDirectKotlinSubtypesOf(fqName: FqName): Sequence<FqNameWrapper> = storage?.getSubtypesOf(fqName, deep = false)
        ?.map(FqNameWrapper.Companion::createFromFqName)
        .orEmpty()

    private fun getDirectJavaSubtypesOf(compilerRefProvider: (NameEnumerator) -> CompilerRef?): Sequence<FqNameWrapper> =
        compilerReferenceServiceBase?.getDirectInheritorsNames(
            CompilerReferenceServiceBase.CompilerRefProvider { compilerRefProvider(it) }
        )
            ?.asSequence()
            ?.mapNotNull(FqNameWrapper.Companion::createFromSearchId)
            .orEmpty()

    private fun getDirectJavaSubtypesOf(hierarchyElement: PsiElement): Sequence<FqNameWrapper> =
        LanguageCompilerRefAdapter.findAdapter(hierarchyElement, true)?.let { adapter ->
            getDirectJavaSubtypesOf { adapter.asCompilerRef(hierarchyElement, it) }
        }.orEmpty()

    private val compilerReferenceServiceBase: CompilerReferenceServiceBase<*>?
        get() = CompilerReferenceService.getInstanceIfEnabled(project)?.safeAs<CompilerReferenceServiceBase<*>>()

    private val isInsideLibraryScopeThreadLocal = ThreadLocal.withInitial { false }
    private fun isInsideLibraryScope(): Boolean =
        compilerReferenceServiceBase?.isInsideLibraryScope
            ?: isInsideLibraryScopeThreadLocal.get()

    private fun <T> computeInLibraryScope(action: () -> T): T =
        compilerReferenceServiceBase?.computeInLibraryScope<T, Throwable>(action)
            ?: run {
                isInsideLibraryScopeThreadLocal.set(true)
                try {
                    action()
                } finally {
                    isInsideLibraryScopeThreadLocal.set(false)
                }
            }

    private fun findHierarchyInLibrary(hierarchyElement: PsiElement): Set<PsiElement> {
        val overridden: MutableSet<PsiElement> = linkedSetOf(hierarchyElement)
        val processor = Processor { clazz: PsiClass ->
            clazz.takeUnless { it.hasModifierProperty(PsiModifier.PRIVATE) }?.let { overridden += clazz }
            true
        }

        HierarchySearchRequest(
            originalElement = hierarchyElement,
            searchScope = ProjectScope.getLibrariesScope(project),
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
        private val LOG = logger<KotlinCompilerReferenceIndexService>()
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

private fun extractFqNames(element: PsiElement): List<FqName>? {
    extractFqName(element)?.let { return listOf(it) }
    return when (element) {
        is PsiMethod -> extractFqNamesFromPsiMethod(element)
        is KtParameter -> extractFqNamesFromParameter(element)
        else -> null
    }
}

private fun extractFqName(element: PsiElement): FqName? = when (element) {
    is KtClassOrObject, is PsiClass -> element.getKotlinFqName()
    is KtConstructor<*> -> element.getContainingClassOrObject().fqName
    is KtNamedFunction -> element.fqName
    is KtProperty -> element.fqName
    is PsiField -> element.getKotlinFqName()
    else -> null
}

private fun extractFqNamesFromParameter(parameter: KtParameter): List<FqName>? {
    val parameterFqName = parameter.takeIf(KtParameter::hasValOrVar)?.fqName ?: return null
    val componentFunctionName = parameter.asComponentFunctionName?.let { FqName(parameterFqName.parent().asString() + ".$it") }
    return listOfNotNull(parameterFqName, componentFunctionName)
}

internal val KtParameter.asComponentFunctionName: String?
    get() {
        if (containingClassOrObject?.safeAs<KtClass>()?.isData() != true) return null
        val parameterIndex = parameterIndex().takeUnless { it == -1 }?.plus(1) ?: return null
        return "component$parameterIndex"
    }

private fun extractFqNamesFromPsiMethod(psiMethod: PsiMethod): List<FqName>? {
    if (psiMethod.isConstructor) return psiMethod.containingClass?.getKotlinFqName()?.let(::listOf)

    val fqName = psiMethod.getKotlinFqName() ?: return null
    val listOfFqName = listOf(fqName)
    if (psiMethod.hasModifier(JvmModifier.STATIC)) return listOfFqName

    val name = psiMethod.name
    val parametersSize = psiMethod.parameters.size
    if (psiMethod.hasTypeParameters() ||
        JvmAbi.isGetterName(name) && parametersSize != 0 ||
        JvmAbi.isSetterName(name) && parametersSize != 1
    ) {
        return listOfFqName
    }

    val parentFqName = fqName.parent()
    return listOfFqName + getPropertyNamesCandidatesByAccessorName(Name.identifier(name)).map { parentFqName.child(it) }
}
