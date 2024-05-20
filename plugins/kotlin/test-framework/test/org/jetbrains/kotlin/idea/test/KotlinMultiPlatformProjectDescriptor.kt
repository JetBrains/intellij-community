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
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

/**
 * The project is created with three modules: Common, Jvm -> Common, Js -> Common.
 *
 * Standard library and kotlinx-coroutines-core of fixed versions are added to all modules.
 *
 * Since we can't use Gradle in light fixture tests due to performance reasons, correct libraries should be mapped to modules manually.
 */
object KotlinMultiPlatformProjectDescriptor : KotlinLightProjectDescriptor() {
    enum class PlatformDescriptor(
        val moduleName: String,
        val targetPlatform: TargetPlatform,
        val isKotlinSdkUsed: Boolean = true,
        val refinementDependencies: List<PlatformDescriptor> = emptyList(),
        val libraryDependencies: List<KmpAwareLibraryDependency> = emptyList(),
    ) {
        COMMON(
            moduleName = "Common",
            targetPlatform = TargetPlatform(
                setOf(
                    JvmPlatforms.jvm8.single(),
                    JsPlatforms.defaultJsPlatform.single()
                )
            ),
            libraryDependencies = listOf(
                KmpAwareLibraryDependency.allMetadataJar("org.jetbrains.kotlin:kotlin-stdlib:commonMain:1.9.23"), // TODO (KTIJ-29725): sliding version
                KmpAwareLibraryDependency.metadataKlib("org.jetbrains.kotlinx:kotlinx-coroutines-core:commonMain:1.8.0")
            ),
        ),
        JVM(
            moduleName = "Jvm",
            targetPlatform = JvmPlatforms.jvm8,
            isKotlinSdkUsed = false,
            refinementDependencies = listOf(COMMON),
            libraryDependencies = listOf(
                KmpAwareLibraryDependency.jar("org.jetbrains.kotlin:kotlin-stdlib:1.9.23"),
                KmpAwareLibraryDependency.jar("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0"),
                KmpAwareLibraryDependency.jar("org.jetbrains:annotations:23.0.0"),
            ),
        ),
        JS(
            moduleName = "Js",
            targetPlatform = JsPlatforms.defaultJsPlatform,
            refinementDependencies = listOf(COMMON),
            libraryDependencies = listOf(
                KmpAwareLibraryDependency.klib("org.jetbrains.kotlin:kotlin-stdlib-js:1.9.23"),
                KmpAwareLibraryDependency.klib("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.8.0"),
                KmpAwareLibraryDependency.klib("org.jetbrains.kotlinx:atomicfu-js:0.23.1"),
            ),
        );

        val sourceRootName: String
            get() = "src_${moduleName.lowercase()}"

        fun sourceRoot(): VirtualFile? = findRoot(sourceRootName)

        private fun findRoot(rootName: String?): VirtualFile? =
            if (rootName == null) null
            else TempFileSystem.getInstance().findFileByPath("/${rootName}")
                ?: throw IllegalStateException("Cannot find temp:///${rootName}")
    }

    override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk9()

    override fun setUpProject(project: Project, handler: SetupHandler) {
        super.setUpProject(project, handler)

        runWriteAction {
            val descriptorsFromCommonToPlatform =
                topologicalSort(PlatformDescriptor.entries, reverseOrder = true, PlatformDescriptor::refinementDependencies)

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
        val allUniqueDependencies = PlatformDescriptor.entries.flatMap(PlatformDescriptor::libraryDependencies).toSet()

        return allUniqueDependencies.associateWith { kmpDependency ->
            createLibraryFromCoordinates(project, kmpDependency)
        }
    }

    private fun createLibraryFromCoordinates(project: Project, dependency: KmpAwareLibraryDependency): Library {
        val dependencyRoot = KmpLightFixtureDependencyDownloader.resolveDependency(dependency)?.toFile()
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
        PlatformDescriptor.entries.asSequence()
            .map { it.sourceRoot() }
            .filterNotNull()
            .flatMap { it.children.asSequence() }
            .forEach { it.delete(this) }
    }
}
