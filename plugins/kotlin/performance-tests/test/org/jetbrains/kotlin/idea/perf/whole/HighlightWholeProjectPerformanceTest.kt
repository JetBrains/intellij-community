/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.perf.whole

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.perf.util.*
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction
import org.jetbrains.kotlin.idea.testFramework.relativePath
import org.jetbrains.kotlin.idea.util.runReadActionInSmartMode
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(JUnit3RunnerWithInners::class)
class HighlightWholeProjectPerformanceTest : UsefulTestCase() {

    override fun setUp() {
        val allowedErrorDescription = setOf(
            "Unknown artifact type: war",
            "Unknown artifact type: exploded-war"
        )

        ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(
            { errorDescription ->
                val description = errorDescription.description
                if (description !in allowedErrorDescription) {
                    throw RuntimeException(description)
                }
                else {
                    logMessage { "project loading error: '$description' at '${errorDescription.elementName}'" }
                }
            }, testRootDisposable
        )
    }

    fun testHighlightAllKtFilesInProject() {
        val emptyProfile = System.getProperty("emptyProfile", "false")!!.toBoolean()
        val projectSpecs = projectSpecs()
        for (projectSpec in projectSpecs) {
            val projectName = projectSpec.name
            val projectPath = projectSpec.path

            suite(suiteName = "allKtFilesIn-$projectName") {
            app {
                warmUpProject()

                with(config) {
                    warmup = 1
                    iterations = 1
                }


                    try {
                        project(ExternalProject(projectPath, ProjectOpenAction.GRADLE_PROJECT), refresh = true) {
                            profile(if (emptyProfile) EmptyProfile else DefaultProfile)

                            val ktFiles = mutableSetOf<VirtualFile>()
                            project.runReadActionInSmartMode {
                                val projectFileIndex = ProjectFileIndex.getInstance(project)
                                val modules = mutableSetOf<Module>()
                                val ktFileProcessor = { ktFile: VirtualFile ->
                                    if (projectFileIndex.isInSourceContent(ktFile)) {
                                        ktFiles.add(ktFile)
                                    }
                                    true
                                }
                                FileTypeIndex.processFiles(KotlinFileType.INSTANCE, ktFileProcessor, GlobalSearchScope.projectScope(project))
                                modules
                            }

                            logStatValue("number of kt files", ktFiles.size)
                            val topMidLastFiles =
                                limitedFiles(ktFiles, 10)
                            logStatValue("limited number of kt files", topMidLastFiles.size)

                            topMidLastFiles.forEach {
                                logMessage { "${project.relativePath(it)} fileSize: ${Files.size(it.toNioPath())}" }
                            }

                            topMidLastFiles.forEachIndexed { idx, file ->
                                logMessage { "$idx / ${topMidLastFiles.size} : ${project.relativePath(file)} fileSize: ${Files.size(file.toNioPath())}" }

                                try {
                                    fixture(file).use {
                                        measure<List<HighlightInfo>>(it.fileName, clearCaches = false) {
                                            test = {
                                                highlight(it)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    // nothing as it is already caught by perfTest
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // nothing as it is already caught by perfTest
                    }
                }
            }
        }
    }

    private fun limitedFiles(ktFiles: Collection<VirtualFile>, partPercent: Int): Collection<VirtualFile> {
        val sortedBySize = ktFiles
            .filter { Files.size(it.toNioPath()) > 0 }
            .map { it to Files.size(it.toNioPath()) }.sortedByDescending { it.second }
        val percentOfFiles = (sortedBySize.size * partPercent) / 100

        val topFiles = sortedBySize.take(percentOfFiles).map { it.first }
        val midFiles =
            sortedBySize.take(sortedBySize.size / 2 + percentOfFiles / 2).takeLast(percentOfFiles).map { it.first }
        val lastFiles = sortedBySize.takeLast(percentOfFiles).map { it.first }

        return LinkedHashSet(topFiles + midFiles + lastFiles)
    }

    private fun projectSpecs(): List<ProjectSpec> {
        val projects = System.getProperty("performanceProjects") ?: return emptyList()
        return projects.split(",").map {
            val idx = it.indexOf("=")
            if (idx <= 0) ProjectSpec(it, "../$it") else ProjectSpec(it.substring(0, idx), it.substring(idx + 1))
        }.filter {
            val path = File(it.path)
            path.exists() && path.isDirectory
        }
    }

    private data class ProjectSpec(val name: String, val path: String)
}
