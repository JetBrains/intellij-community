// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

/**
 * The project is created with three modules: Common, Jvm -> Common, Js -> Common.
 *
 * Currently, no libraries are added.
 */
object KotlinMultiPlatformProjectDescriptor : KotlinLightProjectDescriptor() {
    enum class PlatformDescriptor(
        val moduleName: String,
        val sourceRootName: String? = null,
        val targetPlatform: TargetPlatform,
        val isKotlinSdkUsed: Boolean = true,
        val refinementDependencies: List<PlatformDescriptor> = emptyList(),
    ) {
        COMMON(
            moduleName = "Common",
            sourceRootName = "src_common",
            targetPlatform = TargetPlatform(
                setOf(
                    JvmPlatforms.jvm8.single(),
                    JsPlatforms.defaultJsPlatform.single()
                )
            ),
        ),
        JVM(
            moduleName = "Jvm",
            sourceRootName = "src_jvm",
            targetPlatform = JvmPlatforms.jvm8,
            isKotlinSdkUsed = false,
            refinementDependencies = listOf(COMMON),
        ),
        JS(
            moduleName = "Js",
            sourceRootName = "src_js",
            targetPlatform = JsPlatforms.defaultJsPlatform,
            refinementDependencies = listOf(COMMON),
        );

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
            val common = makeModule(project, PlatformDescriptor.COMMON)

            val jvm = makeModule(project, PlatformDescriptor.JVM)
            ModuleRootModificationUtil.addDependency(jvm, common)

            val js = makeModule(project, PlatformDescriptor.JS)
            ModuleRootModificationUtil.addDependency(js, common)
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    private fun makeModule(project: Project, descriptor: PlatformDescriptor): Module {
        val path = "${FileUtil.getTempDirectory()}/${descriptor.moduleName}.iml"
        val module = createModule(project, path)
        ModuleRootModificationUtil.updateModel(module) { configureModule(module, it, descriptor) }
        return module
    }

    private fun configureModule(module: Module, model: ModifiableRootModel, descriptor: PlatformDescriptor) {
        model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.HIGHEST
        if (descriptor.sourceRootName != null) {
            val sourceRoot = createSourceRoot(module, descriptor.sourceRootName)
            model.addContentEntry(sourceRoot).addSourceFolder(sourceRoot, JavaSourceRootType.SOURCE)
        }

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

        module.createMultiplatformFacetM3(
            platformKind = descriptor.targetPlatform,
            useProjectSettings = false,
            dependsOnModuleNames = descriptor.refinementDependencies.map(PlatformDescriptor::moduleName),
            pureKotlinSourceFolders = listOf(descriptor.sourceRoot()!!.path),
        )
    }

    fun cleanupSourceRoots() = runWriteAction {
        PlatformDescriptor.entries.asSequence()
            .map { it.sourceRoot() }
            .filterNotNull()
            .flatMap { it.children.asSequence() }
            .forEach { it.delete(this) }
    }
}
