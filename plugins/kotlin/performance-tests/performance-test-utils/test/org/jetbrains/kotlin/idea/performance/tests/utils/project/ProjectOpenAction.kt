// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.performance.tests.utils.project

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.setTrusted
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.UsefulTestCase.assertTrue
import com.intellij.util.io.exists
import org.jetbrains.kotlin.idea.configuration.getModulesWithKotlinFiles
import org.jetbrains.kotlin.idea.configuration.ui.KotlinConfigurationCheckerService
import org.jetbrains.kotlin.idea.performance.tests.utils.closeProject
import org.jetbrains.kotlin.idea.performance.tests.utils.dispatchAllInvocationEvents
import org.jetbrains.kotlin.idea.performance.tests.utils.logMessage
import org.jetbrains.kotlin.idea.performance.tests.utils.runAndMeasure
import org.jetbrains.kotlin.idea.project.ResolveElementCache
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertTrue

data class OpenProject(val projectPath: String, val projectName: String, val jdk: Sdk, val projectOpenAction: ProjectOpenAction)

enum class ProjectOpenAction {
    SIMPLE_JAVA_MODULE {
        override fun openProject(
            application: TestApplicationManager,
            projectPath: String,
            projectName: String,
            jdk: Sdk
        ): Project {
            val project = ProjectManagerEx.getInstanceEx().loadAndOpenProject(projectPath)!!

            return openingProject(application, project) {
                val projectFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(projectPath))!!

                val modulePath = "$projectPath/$name${ModuleFileType.DOT_DEFAULT_EXTENSION}"
                val srcFile = projectFile.findChild("src")!!

                project.setupJdk(jdk)

                //val module = runWriteAction {
                //
                //    val module = ModuleManager.getInstance(project).newModule(modulePath, ModuleTypeId.JAVA_MODULE)
                //    PsiTestUtil.addSourceRoot(module, srcFile)
                //
                //    module
                //}

                //ConfigLibraryUtil.configureKotlinRuntimeAndSdk(module, jdk)
                //ConfigLibraryUtil.configureSdk(module, jdk)
            }
        }
    },

    EXISTING_IDEA_PROJECT {
        override fun openProject(
            application: TestApplicationManager,
            projectPath: String,
            projectName: String,
            jdk: Sdk
        ): Project {
            val projectManager = ProjectManagerEx.getInstanceEx()

            val project =
                projectManager
                    .openProject(
                        Path.of(projectPath),
                        OpenProjectTask { this.projectName = projectName; showWelcomeScreen = false }
                    )
                    ?: error("project $projectName at $projectPath is not loaded")

            return openingProject(application, project) {
                with(project) {
                    trusted()
                    setupJdk(jdk)
                }

                assertTrue(projectManager.isProjectOpened(project), "project $projectName at $projectPath is not opened")
            }
        }
    },

    GRADLE_PROJECT {
        override fun openProject(
            application: TestApplicationManager,
            projectPath: String,
            projectName: String,
            jdk: Sdk
        ): Project {
            check(System.getProperty("org.gradle.native") == "false") {
                "Please specify -Dorg.gradle.native=false due to known Gradle native issue"
            }
            val path = File(projectPath).absolutePath
            val project = ProjectManagerEx.getInstanceEx().loadAndOpenProject(path)!!
            assertTrue(
                !project.isDisposed,
                "Gradle project $projectName at $path is accidentally disposed immediately after import"
            )

            with(project) {
                trusted()
                setupJdk(jdk)
            }

            refreshGradleProject(path, project)

            return project
        }

        private fun refreshGradleProjectIfNeeded(projectPath: String, project: Project) {
            if (listOf("build.gradle.kts", "build.gradle").map { name -> Paths.get(projectPath, name).exists() }
                    .find { e -> e } != true) return

            ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(true)

            refreshGradleProject(projectPath, project)

            dispatchAllInvocationEvents()

            // WARNING: [VD] DO NOT SAVE PROJECT AS IT COULD PERSIST WRONG MODULES INFO

            //        runInEdtAndWait {
            //            PlatformTestUtil.saveProject(project)
            //        }
        }

        override fun postOpenProject(project: Project, openProject: OpenProject) {
            runAndMeasure("refresh gradle project ${openProject.projectName}") {
                refreshGradleProjectIfNeeded(openProject.projectPath, project)
            }

            super.postOpenProject(project, openProject)
        }
    };

    abstract fun openProject(application: TestApplicationManager, projectPath: String, projectName: String, jdk: Sdk): Project

    protected fun Project.setupJdk(jdk: Sdk) = runWriteAction {
        ProjectRootManager.getInstance(this).projectSdk = jdk
    }

    protected fun Project.trusted() = this.setTrusted(true)

    protected fun openingProject(application: TestApplicationManager, project: Project, action: () -> Unit): Project {
        return try {
            action()
            project
        } catch (e: Throwable) {
            application.closeProject(project)
            throw e
        }
    }

    open fun postOpenProject(project: Project, openProject: OpenProject) {
        ApplicationManager.getApplication().executeOnPooledThread {
            ResolveElementCache.forceFullAnalysisModeInTests = true
            DumbService.getInstance(project).waitForSmartMode()

            val checkerService = KotlinConfigurationCheckerService.getInstance(project)
            val writeActionContinuations = mutableListOf<() -> Unit>()
            for (module in getModulesWithKotlinFiles(project)) {
                checkerService.getAndCacheLanguageLevelByDependencies(module, writeActionContinuations)
            }
            if (writeActionContinuations.isNotEmpty()) {
                runInEdt {
                    runWriteAction {
                        writeActionContinuations.forEach { it.invoke() }
                    }
                }
            }
        }.get()

        val modules = ModuleManager.getInstance(project).modules
        assertTrue("project '${openProject.projectName}' has to have at least one module", modules.isNotEmpty())

        logMessage { "modules of '${openProject.projectName}': ${modules.map { m -> m.name }}" }
        project.projectFile?.parent?.let { parent ->
            VfsUtil.markDirtyAndRefresh(false, true, true, parent)
        }

        VirtualFileManager.getInstance().syncRefresh()

        //runWriteAction { project.save() }
    }

    companion object {
        fun openProject(openProject: OpenProject): Project {
            val project = openProject.projectOpenAction.openProject(
                application = TestApplicationManager.getInstance(),
                projectPath = openProject.projectPath,
                projectName = openProject.projectName,
                jdk = openProject.jdk
            )

            dispatchAllInvocationEvents()

            logMessage { "project ${openProject.projectName} is ${if (project.isInitialized) "initialized" else "not initialized"}" }

            (ChangeListManager.getInstance(project) as ChangeListManagerImpl).waitUntilRefreshed()
            project.save()
            return project
        }
    }
}

