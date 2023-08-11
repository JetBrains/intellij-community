// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.externalSystem.JavaModuleData
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.indexing.DumbModeAccessType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.indices.KotlinPackageIndexUtils
import org.jetbrains.kotlin.idea.base.platforms.*
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.base.util.runReadActionInSmartMode
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.syncNonBlockingReadAction
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinNotConfiguredSuppressedModulesState
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.idea.projectConfiguration.RepositoryDescription
import org.jetbrains.kotlin.idea.projectConfiguration.getDefaultJvmTarget
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.vfilefinder.IdeVirtualFileFinder
import org.jetbrains.kotlin.idea.vfilefinder.KlibMetaFileIndex
import org.jetbrains.kotlin.idea.vfilefinder.KotlinJavaScriptMetaFileIndex
import org.jetbrains.kotlin.idea.vfilefinder.hasSomethingInPackage
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.isWasm
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative

private val LOG = Logger.getInstance("#org.jetbrains.kotlin.idea.configuration.ConfigureKotlinInProjectUtils")

val LAST_SNAPSHOT_VERSION: IdeKotlinVersion = IdeKotlinVersion.get("1.5.255-SNAPSHOT")

val SNAPSHOT_REPOSITORY = RepositoryDescription(
    "sonatype.oss.snapshots",
    "Sonatype OSS Snapshot Repository",
    "https://oss.sonatype.org/content/repositories/snapshots",
    null,
    isSnapshot = true
)

val DEFAULT_GRADLE_PLUGIN_REPOSITORY = RepositoryDescription(
    "default.gradle.plugins",
    "Default Gradle Plugin Repository",
    "https://plugins.gradle.org/m2/",
    null,
    isSnapshot = false
)

fun devRepository(version: IdeKotlinVersion) = RepositoryDescription(
    "teamcity.kotlin.dev",
    "Teamcity Repository of Kotlin Development Builds",
    "https://teamcity.jetbrains.com/guestAuth/app/rest/builds/buildType:(id:Kotlin_KotlinPublic_Aggregate),number:${version.rawVersion},branch:(default:any)/artifacts/content/maven",
    null,
    isSnapshot = false
)

const val MAVEN_CENTRAL = "mavenCentral()"

const val JCENTER = "jcenter()"

const val KOTLIN_GROUP_ID = "org.jetbrains.kotlin"

fun isRepositoryConfigured(repositoriesBlockText: String): Boolean =
    repositoriesBlockText.contains(MAVEN_CENTRAL) || repositoriesBlockText.contains(JCENTER)

fun DependencyScope.toGradleCompileScope(isAndroidModule: Boolean) = when (this) {
    DependencyScope.COMPILE -> "implementation"
    // TODO: We should add testCompile or androidTestCompile
    DependencyScope.TEST -> if (isAndroidModule) "implementation" else "testImplementation"
    DependencyScope.RUNTIME -> "runtime"
    DependencyScope.PROVIDED -> "implementation"
    else -> "implementation"
}

fun RepositoryDescription.toGroovyRepositorySnippet() = "maven { url '$url' }"

/**
 * This syntax has been released in kotlin-dsl-0.11.1 release and in Gradle 4.2-RC1 release.
 *
 * We already require Gradle distribution of higher version (see `checkGradleCompatibility` function),
 * so it is safe to use this syntax everywhere.
 */
fun RepositoryDescription.toKotlinRepositorySnippet() = "maven(\"$url\")"

fun getRepositoryForVersion(version: IdeKotlinVersion): RepositoryDescription? = when {
    version.isSnapshot -> SNAPSHOT_REPOSITORY
    version.isDev -> devRepository(version)
    else -> null
}

fun isModuleConfigured(moduleSourceRootGroup: ModuleSourceRootGroup): Boolean {
    return allConfigurators().any {
        it.getStatus(moduleSourceRootGroup) == ConfigureKotlinStatus.CONFIGURED
    }
}

/**
 * Returns a list of modules which contain sources in Kotlin.
 * Note that this method is expensive and should not be called more often than strictly necessary.
 *
 * DO NOT CALL THIS ON AWT THREAD
 */
@RequiresBackgroundThread
fun getModulesWithKotlinFiles(project: Project, modulesWithKotlinFacets: List<Module>? = null): Collection<Module> {
    if (!isUnitTestMode() && isDispatchThread()) {
        LOG.error("getModulesWithKotlinFiles could be a heavy operation and should not be call on AWT thread")
    }

    fun <T> nonBlockingReadActionSync(block: () -> T): T {
        return ReadAction.nonBlocking(block)
            .expireWith(KotlinPluginDisposable.getInstance(project))
            .executeSynchronously()
    }

    val projectScope = project.projectScope()
    // nothing to configure if there is no Kotlin files in entire project
    val anyKotlinFileInProject = nonBlockingReadActionSync {
        FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, projectScope)
    }
    if (!anyKotlinFileInProject) {
        return emptyList()
    }

    val projectFileIndex = ProjectFileIndex.getInstance(project)

    val modules =
        if (modulesWithKotlinFacets.isNullOrEmpty()) {
            nonBlockingReadActionSync {
                val kotlinFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, projectScope)
                kotlinFiles.mapNotNullTo(mutableSetOf()) { ktFile: VirtualFile ->
                    if (projectFileIndex.isInSourceContent(ktFile)) {
                        projectFileIndex.getModuleForFile(ktFile)
                    } else null
                }
            }

        } else {
            // filter modules with Kotlin facet AND have at least a single Kotlin file in them
            nonBlockingReadActionSync {
                modulesWithKotlinFacets.filterTo(mutableSetOf()) { module ->
                    if (module.isDisposed) return@filterTo false

                    FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, module.moduleScope)
                }
            }
        }
    return modules
}

/**
 * Returns a list of modules which contain sources in Kotlin, grouped by base module.
 * Note that this method is expensive and should not be called more often than strictly necessary.
 */
fun getConfigurableModulesWithKotlinFiles(project: Project): List<ModuleSourceRootGroup> {
    val modules = getModulesWithKotlinFiles(project)
    if (modules.isEmpty()) return emptyList()

    return ModuleSourceRootMap(project).groupByBaseModules(modules)
}

fun showConfigureKotlinNotificationIfNeeded(module: Module) {
    val action: () -> Unit = {
        val moduleGroup = module.toModuleGroup()
        if (isNotConfiguredNotificationRequired(moduleGroup)) {
            ConfigureKotlinNotificationManager.notify(module.project)
        }
    }

    val dumbService = DumbService.getInstance(module.project)
    if (dumbService.isDumb) {
        dumbService.smartInvokeLater { action() }
    } else {
        action()
    }
}

fun isNotConfiguredNotificationRequired(moduleGroup: ModuleSourceRootGroup): Boolean {
    return KotlinNotConfiguredSuppressedModulesState.shouldSuggestConfiguration(moduleGroup.baseModule)
            && !isModuleConfigured(moduleGroup)
}

fun getAbleToRunConfigurators(project: Project): Collection<KotlinProjectConfigurator> {
    val modules = getConfigurableModules(project)

    return allConfigurators().filter { configurator ->
        modules.any { configurator.getStatus(it) == ConfigureKotlinStatus.CAN_BE_CONFIGURED }
    }
}

fun getConfigurableModules(project: Project): List<ModuleSourceRootGroup> {
    return getConfigurableModulesWithKotlinFiles(project).ifEmpty {
        ModuleSourceRootMap(project).groupByBaseModules(project.modules.asList())
    }
}

fun getAbleToRunConfigurators(module: Module): Collection<KotlinProjectConfigurator> {
    val moduleGroup = module.toModuleGroup()
    return allConfigurators().filter {
        it.getStatus(moduleGroup) == ConfigureKotlinStatus.CAN_BE_CONFIGURED
    }
}

fun getConfiguratorByName(name: String): KotlinProjectConfigurator? {
    return allConfigurators().firstOrNull { it.name == name }
}

fun allConfigurators(): Array<KotlinProjectConfigurator> {
    @Suppress("DEPRECATION")
    return Extensions.getExtensions(KotlinProjectConfigurator.EP_NAME)
}

fun getCanBeConfiguredModules(project: Project, configurator: KotlinProjectConfigurator): List<Module> {
    val projectModules = project.modules.asList()
    val result = mutableListOf<Module>()
    val progressIndicator = ProgressManager.getGlobalProgressIndicator()
    for ((index, module) in ModuleSourceRootMap(project).groupByBaseModules(projectModules).withIndex()) {
        if (!isUnitTestMode()) {
            progressIndicator?.let {
                it.checkCanceled()
                it.fraction = index * 1.0 / projectModules.size
                it.text2 = KotlinProjectConfigurationBundle.message("lookup.module.0.configuration.progress.text", module.baseModule.name)
            }
        }
        if (configurator.canConfigure(module)) {
            result.add(module.baseModule)
        }
    }
    return result
}

private fun KotlinProjectConfigurator.canConfigure(moduleSourceRootGroup: ModuleSourceRootGroup) =
    getStatus(moduleSourceRootGroup) == ConfigureKotlinStatus.CAN_BE_CONFIGURED &&
            (allConfigurators().toList() - this).none { it.getStatus(moduleSourceRootGroup) == ConfigureKotlinStatus.CONFIGURED }

/**
 * Returns a list of modules which contain sources in Kotlin and for which it's possible to run the given configurator.
 * Note that this method is expensive and should not be called more often than strictly necessary.
 */
fun getCanBeConfiguredModulesWithKotlinFiles(project: Project, configurator: KotlinProjectConfigurator): List<Module> {
    val modules = getConfigurableModulesWithKotlinFiles(project)
    return modules.filter { configurator.getStatus(it) == ConfigureKotlinStatus.CAN_BE_CONFIGURED }.map { it.baseModule }
}

fun getConfigurationPossibilitiesForConfigureNotification(
    project: Project,
    excludeModules: Collection<Module> = emptyList()
): Pair<Collection<ModuleSourceRootGroup>, Collection<KotlinProjectConfigurator>> {
    val modulesWithKotlinFiles = getConfigurableModulesWithKotlinFiles(project).exclude(excludeModules)
    val configurators = allConfigurators()

    val runnableConfigurators = mutableSetOf<KotlinProjectConfigurator>()
    val configurableModules = mutableListOf<ModuleSourceRootGroup>()

    // We need to return all modules for which at least one configurator is applicable, as well as all configurators which
    // are applicable for at least one module. At the same time we want to call getStatus() only once for each module/configurator pair.
    for (moduleSourceRootGroup in modulesWithKotlinFiles) {
        var moduleCanBeConfigured = false
        var moduleAlreadyConfigured = false
        for (configurator in configurators) {
            if (moduleCanBeConfigured && configurator in runnableConfigurators) continue
            when (configurator.getStatus(moduleSourceRootGroup)) {
                ConfigureKotlinStatus.CAN_BE_CONFIGURED -> {
                    moduleCanBeConfigured = true
                    runnableConfigurators.add(configurator)
                }

                ConfigureKotlinStatus.CONFIGURED -> moduleAlreadyConfigured = true
                else -> {
                }
            }
        }
        if (moduleCanBeConfigured
            && !moduleAlreadyConfigured
            && KotlinNotConfiguredSuppressedModulesState.shouldSuggestConfiguration(moduleSourceRootGroup.baseModule)
        ) {
            configurableModules.add(moduleSourceRootGroup)
        }
    }

    return configurableModules to runnableConfigurators
}

fun findApplicableConfigurator(module: Module): KotlinProjectConfigurator {
    val moduleGroup = module.toModuleGroup()
    return allConfigurators().find { it.getStatus(moduleGroup) != ConfigureKotlinStatus.NON_APPLICABLE }
        ?: KotlinJavaModuleConfigurator.instance
}

/**
 * Returns true if the Kotlin compiler plugin (Gradle/Maven/JPS) is enabled
 * in the module and the Kotlin compiler is set up.
 */
fun Module.hasKotlinPluginEnabled(): Boolean {
    if (buildSystemType == BuildSystemType.JPS) {
        // JPS uses the built-in Kotlin compiler as soon as there is any Kotlin stdlib
        // on the classpath, even from transitive dependencies.
        return hasAnyKotlinRuntimeInScope(this)
    } else {
        val settings = KotlinFacetSettingsProvider.getInstance(project)
        val moduleSettings = settings?.getSettings(this) ?: return false
        return moduleSettings.compilerSettings != null
    }
}

fun hasAnyKotlinRuntimeInScope(module: Module): Boolean {
    return syncNonBlockingReadAction(module.project) {
        val scope = module.getModuleWithDependenciesAndLibrariesScope(true)
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
            scope.hasKotlinJvmRuntime(module.project)
                    || runReadAction { hasKotlinJsKjsmFile(LibraryKindSearchScope(module, scope, KotlinJavaScriptLibraryKind)) }
                    || hasKotlinCommonRuntimeInScope(module)
                    || hasKotlinCommonLegacyRuntimeInScope(scope)
                    || hasKotlinJsRuntimeInScope(module)
                    || hasKotlinWasmRuntimeInScope(module)
                    || hasKotlinNativeRuntimeInScope(module)
        })
    }
}

fun isStdlibModule(module: Module): Boolean {
    return KotlinPackageIndexUtils.packageExists(FqName("kotlin"), module.moduleProductionSourceScope)
}

fun getPlatform(module: Module): String {
    return when {
        module.platform.isJvm() -> {
            if (module.name.contains("android")) "jvm.android"
            else "jvm"
        }
        module.platform.isWasm() && hasKotlinWasmRuntimeInScope(module) -> "wasm"
        module.platform.isJs() && hasKotlinJsRuntimeInScope(module) -> "js"
        module.platform.isCommon() -> "common"
        module.platform.isNative() -> "native." + (module.platform?.componentPlatforms?.first()?.targetName ?: "unknown")
        else -> "unknown"
    }
}

fun hasKotlinJvmRuntimeInScope(module: Module): Boolean {
    return syncNonBlockingReadAction(module.project) {
        val scope = module.getModuleWithDependenciesAndLibrariesScope(true)
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode<Boolean, Throwable> {
            scope.hasKotlinJvmRuntime(module.project)
        }
    }
}

fun hasKotlinJsLegacyRuntimeInScope(module: Module): Boolean {
    return syncNonBlockingReadAction(module.project) {
        val scope = module.getModuleWithDependenciesAndLibrariesScope(true)
        runReadAction {
            hasKotlinJsKjsmFile(LibraryKindSearchScope(module, scope, KotlinJavaScriptLibraryKind))
        }
    }
}

/**
 * Will check if kotlin is present as klib (knm files)
 */
fun hasKotlinCommonRuntimeInScope(module: Module): Boolean {
    return hasKotlinPlatformRuntimeInScope(module, StandardNames.BUILT_INS_PACKAGE_FQ_NAME, KotlinCommonLibraryKind)
}

/**
 * Will check if kotlin is present as .kotlin_metadata (legacy) file
 */
fun hasKotlinCommonLegacyRuntimeInScope(scope: GlobalSearchScope): Boolean {
    return IdeVirtualFileFinder(scope).hasMetadataPackage(StandardNames.BUILT_INS_PACKAGE_FQ_NAME)
}

fun hasKotlinJsRuntimeInScope(module: Module): Boolean {
    return hasKotlinPlatformRuntimeInScope(module, KOTLIN_JS_FQ_NAME, KotlinJavaScriptLibraryKind)
}

fun hasKotlinWasmRuntimeInScope(module: Module): Boolean {
    return hasKotlinPlatformRuntimeInScope(module, KOTLIN_WASM_FQ_NAME, KotlinWasmLibraryKind)
}

fun hasKotlinNativeRuntimeInScope(module: Module): Boolean {
    return hasKotlinPlatformRuntimeInScope(module, KOTLIN_NATIVE_FQ_NAME, KotlinNativeLibraryKind)
}

fun hasKotlinPlatformRuntimeInScope(
    module: Module,
    fqName: FqName,
    libraryKind: PersistentLibraryKind<*>
    ): Boolean {
    return module.project.runReadActionInSmartMode {
        val scope = module.getModuleWithDependenciesAndLibrariesScope(true)
        hasSomethingInPackage(KlibMetaFileIndex.NAME, fqName, LibraryKindSearchScope(module, scope, libraryKind))
    }
}

typealias ModuleName = String
typealias TargetJvm = String?

fun getModulesTargetingUnsupportedJvmAndTargetsForAllModules(
    modulesToConfigure: List<Module>,
    kotlinVersion: IdeKotlinVersion
): Pair<Map<String, List<String>>, Map<ModuleName, TargetJvm>> {
    val modulesAndJvmTargets = mutableMapOf<ModuleName, TargetJvm>()
    val jvmModulesTargetingUnsupportedJvm = mutableMapOf<String,  MutableList<String>>()
    for (module in modulesToConfigure) {
        val jvmTarget = getTargetBytecodeVersionFromModule(module, kotlinVersion)
        modulesAndJvmTargets[module.name] = jvmTarget
        jvmTarget?.removePrefix("1.")?.toIntOrNull()?.let {
            if (it < 8) {
                val modulesForThisTarget = jvmModulesTargetingUnsupportedJvm.getOrDefault(jvmTarget, mutableListOf())
                modulesForThisTarget.add(module.name)
                jvmModulesTargetingUnsupportedJvm[jvmTarget] = modulesForThisTarget
            }
        }
    }
    return Pair(jvmModulesTargetingUnsupportedJvm, modulesAndJvmTargets)
}

fun getTargetBytecodeVersionFromModule(
    module: Module,
    kotlinVersion: IdeKotlinVersion
): String? {
    val projectPath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null
    val project = module.project
    return ExternalSystemApiUtil.findModuleNode(project, ProjectSystemId("GRADLE"), projectPath)?.let { moduleDataNode ->
        val javaModuleData = ExternalSystemApiUtil.find(moduleDataNode, JavaModuleData.KEY)
        javaModuleData?.let {
            javaModuleData.data.targetBytecodeVersion
        }
    } ?: getJvmTargetFromSdkOrDefault(module, kotlinVersion)
}

private fun getJvmTargetFromSdkOrDefault(
    module: Module,
    kotlinVersion: IdeKotlinVersion
): String? {
    val sdk = module.let { ModuleRootManager.getInstance(it).sdk }
    return getDefaultJvmTarget(sdk, kotlinVersion)?.description
}

private val KOTLIN_JS_FQ_NAME = FqName("kotlin.js")
private val KOTLIN_WASM_FQ_NAME = FqName("kotlin.wasm")

private val KOTLIN_NATIVE_FQ_NAME = FqName("kotlin.native")

private fun hasKotlinJsKjsmFile(scope: GlobalSearchScope): Boolean {
    return hasSomethingInPackage(KotlinJavaScriptMetaFileIndex.NAME, KOTLIN_JS_FQ_NAME, scope)
}

class LibraryKindSearchScope(
    val module: Module,
    baseScope: GlobalSearchScope,
    private val libraryKind: PersistentLibraryKind<*>
) : DelegatingGlobalSearchScope(baseScope) {
    override fun contains(file: VirtualFile): Boolean {
        if (!super.contains(file)) return false
        val orderEntry = ModuleRootManager.getInstance(module).fileIndex.getOrderEntryForFile(file)
        if (orderEntry is LibraryOrderEntry) {
            return detectLibraryKind(orderEntry.library as LibraryEx, module.project) == libraryKind
        }
        return true
    }
}