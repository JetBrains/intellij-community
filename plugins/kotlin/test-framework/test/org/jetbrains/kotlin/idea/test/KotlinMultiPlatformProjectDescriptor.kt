// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.container.topologicalSort
import org.jetbrains.kotlin.idea.artifacts.KmpAwareLibraryDependency
import org.jetbrains.kotlin.idea.artifacts.KmpLightFixtureDependencyDownloader
import org.jetbrains.kotlin.idea.artifacts.KotlinNativeVersion
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import kotlin.sequences.forEach

/**
 * The project is created with the following module structure:
 * ```
 * Common --- Js
 *       \--- Jvm
 *       \--- Native --- MinGW
 *                  \--- Linux --- LinuxX64
 *                            \--- LinuxArm64
 * ```
 * Standard library and kotlinx-coroutines-core of fixed versions are added to all modules.
 *
 * Apple targets require a Mac host and therefore are not included in the test project.
 * The limited set of native targets allows running tests on any host.
 *
 * Host-specific Kotlin/Native distribution library dependencies are not added.
 * One exception is the Kotlin/Native stdlib itself as it's shared by all native targets.
 *
 * Since we can't use Gradle in light fixture tests due to performance reasons, correct libraries are mapped to modules manually.
 *
 * For more details, please refer to [the YT KB article](https://youtrack.jetbrains.com/articles/KTIJ-A-50/Light-Multiplatform-Tests)
 */
class KotlinMultiPlatformProjectDescriptor(
    val platformDescriptors: List<PlatformDescriptor> = PlatformDescriptor.entries
) : KotlinLightProjectDescriptor() {
    enum class PlatformDescriptor(
        val moduleName: String,
        val targetPlatform: TargetPlatform,
        // if true, stub Kotlin SDK is used as module's JDK
        val isKotlinSdkUsed: Boolean,
        // ordered transitive closure of refinement dependencies from platform to common
        val refinementDependencies: List<PlatformDescriptor>,
        // ordered list of library dependencies; transitive dependencies should be listed explicitly
        val libraryDependencies: List<KmpAwareLibraryDependency>,
    ) {
        COMMON(
            moduleName = "Common",
            targetPlatform = TargetPlatform(
                setOf(
                    JvmPlatforms.jvm8.single(),
                    JsPlatforms.defaultJsPlatform.single()
                )
            ),
            isKotlinSdkUsed = true,
            refinementDependencies = emptyList(),
            libraryDependencies = listOf(
                LibraryDependencies.stdlibCommon,
                LibraryDependencies.coroutinesCommonMain,
            ),
        ),
        JVM(
            moduleName = "Jvm",
            targetPlatform = JvmPlatforms.jvm8,
            isKotlinSdkUsed = false,
            refinementDependencies = listOf(COMMON),
            libraryDependencies = listOf(
                LibraryDependencies.stdlibJvm,
                LibraryDependencies.coroutinesJvm,
                LibraryDependencies.jbAnnotationsJvm,
            ),
        ) {
            private val javaSourceRoot: String = "src_jvm_java"
            fun javaSourceRoot(): VirtualFile? = findRoot(javaSourceRoot)

            override fun additionalModuleConfiguration(descriptor: KotlinMultiPlatformProjectDescriptor, module: Module, model: ModifiableRootModel) {
                val sourceRoot = descriptor.createSourceRoot(module, javaSourceRoot)
                model.addContentEntry(sourceRoot).addSourceFolder(sourceRoot, JavaSourceRootType.SOURCE)
            }

            override fun selectSourceRootByFilePath(filePath: String): VirtualFile? =
                if (filePath.endsWith(".java")) javaSourceRoot() else sourceRoot()
        },
        JS(
            moduleName = "Js",
            targetPlatform = JsPlatforms.defaultJsPlatform,
            isKotlinSdkUsed = true,
            refinementDependencies = listOf(COMMON),
            libraryDependencies = listOf(
                LibraryDependencies.stdlibJs,
                LibraryDependencies.coroutinesJs,
                LibraryDependencies.atomicFuJs,
            ),
        ),
        NATIVE(
            moduleName = "Native",
            isKotlinSdkUsed = true,
            targetPlatform = NativePlatforms.nativePlatformByTargets(
                listOf(KonanTarget.LINUX_X64, KonanTarget.LINUX_ARM64, KonanTarget.MINGW_X64)
            ),
            refinementDependencies = listOf(COMMON),
            libraryDependencies = listOf(
                LibraryDependencies.stdlibNative,
                LibraryDependencies.stdlibCommon,
                LibraryDependencies.coroutinesNative,
                LibraryDependencies.coroutinesCommonMain,
                LibraryDependencies.coroutinesConcurrent,
                LibraryDependencies.atomicFuNative,
                LibraryDependencies.atomicFuCommon,
            ),
        ),
        MINGW(
            moduleName = "MinGW",
            isKotlinSdkUsed = true,
            targetPlatform = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.MINGW_X64),
            refinementDependencies = listOf(NATIVE, COMMON),
            libraryDependencies = listOf(
                LibraryDependencies.stdlibNative,
                LibraryDependencies.coroutinesMingw,
                LibraryDependencies.atomicFuMingw,
            ),
        ),
        LINUX(
            moduleName = "Linux",
            targetPlatform = NativePlatforms.nativePlatformByTargets(
                listOf(KonanTarget.LINUX_X64, KonanTarget.LINUX_ARM64)
            ),
            isKotlinSdkUsed = true,
            refinementDependencies = listOf(NATIVE, COMMON),
            libraryDependencies = listOf(
                LibraryDependencies.stdlibNative,
                LibraryDependencies.stdlibCommon,
                LibraryDependencies.coroutinesNative,
                LibraryDependencies.coroutinesCommonMain,
                LibraryDependencies.coroutinesConcurrent,
                LibraryDependencies.coroutinesNativeOther,
                LibraryDependencies.atomicFuNative,
                LibraryDependencies.atomicFuCommon,
            ),
        ),
        LINUX_X64(
            moduleName = "LinuxX64",
            targetPlatform = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.LINUX_X64),
            isKotlinSdkUsed = true,
            refinementDependencies = listOf(LINUX, NATIVE, COMMON),
            libraryDependencies = listOf(
                LibraryDependencies.stdlibNative,
                LibraryDependencies.coroutinesLinuxX64,
                LibraryDependencies.atomicFuLinuxX64,
            ),
        ),
        LINUX_ARM64(
            moduleName = "LinuxArm64",
            targetPlatform = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.LINUX_ARM64),
            isKotlinSdkUsed = true,
            refinementDependencies = listOf(LINUX, NATIVE, COMMON),
            libraryDependencies = listOf(
                LibraryDependencies.stdlibNative,
                LibraryDependencies.coroutinesLinuxArm64,
                LibraryDependencies.atomicFuLinuxArm64,
            ),
        );

        val sourceRootName: String
            get() = "src_${moduleName.lowercase()}"

        fun sourceRoot(): VirtualFile? = findRoot(sourceRootName)

        protected fun findRoot(rootName: String?): VirtualFile? =
            if (rootName == null) null
            else TempFileSystem.getInstance().findFileByPath("/${rootName}")
                ?: throw IllegalStateException("Cannot find temp:///${rootName}")

        open fun additionalModuleConfiguration(descriptor: KotlinMultiPlatformProjectDescriptor, module: Module, model: ModifiableRootModel) = Unit
        open fun selectSourceRootByFilePath(filePath: String): VirtualFile? = sourceRoot()
    }

    override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk9()

    override fun setUpProject(project: Project, handler: SetupHandler) {
        super.setUpProject(project, handler)

        runWriteAction {
            val descriptorsFromCommonToPlatform =
                topologicalSort(platformDescriptors, reverseOrder = true, PlatformDescriptor::refinementDependencies)

            // create libraries beforehand to avoid duplicates
            val projectLibraries = createProjectLibraries(project)
            val modulesByDescriptors = mutableMapOf<PlatformDescriptor, Module>()

            for (descriptor in descriptorsFromCommonToPlatform) {
                val newModule = makeModule(project, descriptor)
                descriptor.refinementDependencies.forEach { refinementDependencyDescriptor ->
                    ModuleRootModificationUtil.addDependency(newModule, modulesByDescriptors.getValue(refinementDependencyDescriptor))
                }
                descriptor.libraryDependencies.forEach { kmpLibraryDependency ->
                    ModuleRootModificationUtil.updateModel(newModule) { model ->
                        val library = projectLibraries.getValue(kmpLibraryDependency)
                        model.addLibraryEntry(library)
                    }
                }

                modulesByDescriptors[descriptor] = newModule
            }
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    private fun createProjectLibraries(project: Project): Map<KmpAwareLibraryDependency, Library> {
        val allUniqueDependencies = platformDescriptors.flatMap(PlatformDescriptor::libraryDependencies).toSet()

        return allUniqueDependencies.associateWith { kmpDependency ->
            createLibraryFromCoordinates(project, kmpDependency)
        }
    }

    private fun createLibraryFromCoordinates(project: Project, dependency: KmpAwareLibraryDependency): Library {
        val transformedLibrariesRoot = KotlinTestDirectoriesHolder.transformedKmpLibrariesRoot
        val dependencyRoot = KmpLightFixtureDependencyDownloader.resolveDependency(dependency, transformedLibrariesRoot)?.toFile()
            ?: error("Unable to download library ${dependency.coordinates}")
        return ConfigLibraryUtil.addProjectLibrary(project = project, name = dependency.coordinates.toString()) {
            addRoot(dependencyRoot, OrderRootType.CLASSES)
            commit()
        }
    }

    private fun makeModule(
        project: Project,
        descriptor: PlatformDescriptor,
    ): Module {
        val path = "${FileUtil.getTempDirectory()}/${descriptor.moduleName}.iml"
        val module = createModule(project, path)
        ModuleRootModificationUtil.updateModel(module) { configureModule(module, it, descriptor) }
        return module
    }

    private fun configureModule(module: Module, model: ModifiableRootModel, descriptor: PlatformDescriptor) {
        model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.HIGHEST
        val sourceRoot = createSourceRoot(module, descriptor.sourceRootName)
        model.addContentEntry(sourceRoot).addSourceFolder(sourceRoot, JavaSourceRootType.SOURCE)

        setUpSdk(module, model, descriptor)

        module.createMultiplatformFacetM3(
            platformKind = descriptor.targetPlatform,
            useProjectSettings = false,
            dependsOnModuleNames = descriptor.refinementDependencies.map(PlatformDescriptor::moduleName),
            pureKotlinSourceFolders = listOf(descriptor.sourceRoot()!!.path),
        )

        descriptor.additionalModuleConfiguration(this, module, model)
    }

    private fun setUpSdk(module: Module, model: ModifiableRootModel, descriptor: PlatformDescriptor) {
        if (descriptor.isKotlinSdkUsed) {
            KotlinSdkType.setUpIfNeeded(module)
            ConfigLibraryUtil.configureSdk(
                module,
                runReadAction { ProjectJdkTable.getInstance() }.findMostRecentSdkOfType(KotlinSdkType.INSTANCE)
                    ?: error("Kotlin SDK wasn't created")
            )
        } else {
            model.sdk = sdk
        }
    }

    fun cleanupSourceRoots() = runWriteAction {
        platformDescriptors.asSequence()
            .map { it.sourceRoot() }
            .filterNotNull()
            .flatMap { it.children.asSequence() }
            .forEach { it.delete(this) }
    }

    companion object {
        val ALL_PLATFORMS = KotlinMultiPlatformProjectDescriptor()
    }
}

private object LibraryDependencies {
    private val STDLIB_VERSION: String
        get() = KotlinNativeVersion.resolvedKotlinGradlePluginVersion
    private val STDLIB_NATIVE_VERSION: String
        get() = KotlinNativeVersion.resolvedKotlinNativeVersion
    private const val COROUTINES_VERSION = "1.8.0"
    private const val ATOMIC_FU_VERSION = "0.23.1"
    private const val JB_ANNOTATIONS_VERSION = "23.0.0"

    private const val KOTLIN_GROUP = "org.jetbrains.kotlin"
    private const val KOTLINX_GROUP = "org.jetbrains.kotlinx"

    private const val STDLIB_ARTIFACT = "kotlin-stdlib"
    private const val COROUTINES_ARTIFACT = "kotlinx-coroutines-core"
    private const val ATOMICFU_ARTIFACT = "atomicfu"

    val stdlibCommon = KmpAwareLibraryDependency.allMetadataJar("$KOTLIN_GROUP:$STDLIB_ARTIFACT:commonMain:$STDLIB_VERSION")
    val stdlibJvm = KmpAwareLibraryDependency.jar("$KOTLIN_GROUP:$STDLIB_ARTIFACT:$STDLIB_VERSION")
    val stdlibJs = KmpAwareLibraryDependency.klib("$KOTLIN_GROUP:$STDLIB_ARTIFACT-js:$STDLIB_VERSION")
    val stdlibNative = KmpAwareLibraryDependency.kotlinNativePrebuilt("klib/common/stdlib:$STDLIB_NATIVE_VERSION")

    val coroutinesCommonMain = KmpAwareLibraryDependency.metadataKlib("$KOTLINX_GROUP:$COROUTINES_ARTIFACT:commonMain:$COROUTINES_VERSION")
    val coroutinesConcurrent =
        KmpAwareLibraryDependency.metadataKlib("$KOTLINX_GROUP:$COROUTINES_ARTIFACT:concurrentMain:$COROUTINES_VERSION")
    val coroutinesNative = KmpAwareLibraryDependency.metadataKlib("$KOTLINX_GROUP:$COROUTINES_ARTIFACT:nativeMain:$COROUTINES_VERSION")
    val coroutinesNativeOther =
        KmpAwareLibraryDependency.metadataKlib("$KOTLINX_GROUP:$COROUTINES_ARTIFACT:nativeOtherMain:$COROUTINES_VERSION")
    val coroutinesJs = KmpAwareLibraryDependency.klib("$KOTLINX_GROUP:$COROUTINES_ARTIFACT-js:$COROUTINES_VERSION")
    val coroutinesJvm = KmpAwareLibraryDependency.jar("$KOTLINX_GROUP:$COROUTINES_ARTIFACT-jvm:$COROUTINES_VERSION")
    val coroutinesMingw = KmpAwareLibraryDependency.klib("$KOTLINX_GROUP:$COROUTINES_ARTIFACT-mingwx64:$COROUTINES_VERSION")
    val coroutinesLinuxX64 = KmpAwareLibraryDependency.klib("$KOTLINX_GROUP:$COROUTINES_ARTIFACT-linuxx64:$COROUTINES_VERSION")
    val coroutinesLinuxArm64 = KmpAwareLibraryDependency.klib("$KOTLINX_GROUP:$COROUTINES_ARTIFACT-linuxarm64:$COROUTINES_VERSION")

    val atomicFuCommon = KmpAwareLibraryDependency.metadataKlib("$KOTLINX_GROUP:$ATOMICFU_ARTIFACT:commonMain:$ATOMIC_FU_VERSION")
    val atomicFuNative = KmpAwareLibraryDependency.metadataKlib("$KOTLINX_GROUP:$ATOMICFU_ARTIFACT:nativeMain:$ATOMIC_FU_VERSION")
    val atomicFuJs = KmpAwareLibraryDependency.klib("$KOTLINX_GROUP:$ATOMICFU_ARTIFACT-js:$ATOMIC_FU_VERSION")
    val atomicFuMingw = KmpAwareLibraryDependency.klib("$KOTLINX_GROUP:$ATOMICFU_ARTIFACT-mingwx64:$ATOMIC_FU_VERSION")
    val atomicFuLinuxX64 = KmpAwareLibraryDependency.klib("$KOTLINX_GROUP:$ATOMICFU_ARTIFACT-linuxx64:$ATOMIC_FU_VERSION")
    val atomicFuLinuxArm64 = KmpAwareLibraryDependency.klib("$KOTLINX_GROUP:$ATOMICFU_ARTIFACT-linuxarm64:$ATOMIC_FU_VERSION")

    val jbAnnotationsJvm = KmpAwareLibraryDependency.jar("org.jetbrains:annotations:$JB_ANNOTATIONS_VERSION")
}
