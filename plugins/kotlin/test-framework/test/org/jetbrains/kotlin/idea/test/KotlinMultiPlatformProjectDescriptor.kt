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
        val sourceRootName: String? = null
    ) {
        COMMON("Common", sourceRootName = "src_common"),
        JVM("Jvm", sourceRootName = "src_jvm"),
        JS("Js", sourceRootName = "src_js");

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

        val setupKotlinSdk: () -> Unit = {
            KotlinSdkType.setUpIfNeeded(module)
            ConfigLibraryUtil.configureSdk(
                module,
                runReadAction { ProjectJdkTable.getInstance() }.findMostRecentSdkOfType(KotlinSdkType.INSTANCE)
                    ?: error("Kotlin SDK wasn't created")
            )
        }
        when (descriptor) {
            PlatformDescriptor.JVM -> {
                model.sdk = sdk
                module.createMultiplatformFacetM3(JvmPlatforms.jvm8, false, listOf("Common"), listOf(descriptor.sourceRoot()!!.path))
            }

            PlatformDescriptor.JS -> {
                setupKotlinSdk()
                module.createMultiplatformFacetM3(JsPlatforms.defaultJsPlatform, false, listOf("Common"), listOf(descriptor.sourceRoot()!!.path))
            }

            PlatformDescriptor.COMMON -> {
                setupKotlinSdk()
                module.createMultiplatformFacetM3(
                    TargetPlatform(
                        setOf(
                            JvmPlatforms.jvm8.single(),
                            JsPlatforms.defaultJsPlatform.single()
                        )
                    ), false, emptyList(), listOf(descriptor.sourceRoot()!!.path)
                )
            }
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
