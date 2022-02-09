// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import org.jetbrains.kotlin.idea.perf.suite.DefaultProfile
import org.jetbrains.kotlin.idea.perf.suite.EmptyProfile
import org.jetbrains.kotlin.idea.perf.suite.suite
import org.jetbrains.kotlin.idea.perf.util.*
import org.jetbrains.kotlin.idea.search.usagesSearch.ExpressionsOfTypeProcessor
import org.jetbrains.kotlin.idea.testFramework.relativePath
import org.jetbrains.kotlin.idea.util.runReadActionInSmartMode
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
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

        ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(testRootDisposable) { errorDescription ->
            val description = errorDescription.description
            if (description !in allowedErrorDescription) {
                throw RuntimeException(description)
            } else {
                logMessage { "project loading error: '$description' at '${errorDescription.elementName}'" }
            }
        }
    }

    fun testHighlightAllKtFilesInProject() {
        val emptyProfile = System.getProperty("emptyProfile", "false")!!.toBoolean()
        val percentOfFiles = System.getProperty("files.percentOfFiles", "10")!!.toInt()
        val maxFilesPerPart = System.getProperty("files.maxFilesPerPart", "300")!!.toInt()
        val minFileSize = System.getProperty("files.minFileSize", "300")!!.toInt()
        val warmUpIterations = System.getProperty("iterations.warmup", "1")!!.toInt()
        val numberOfIterations = System.getProperty("iterations.number", "10")!!.toInt()
        val clearPsiCaches = System.getProperty("caches.clearPsi", "true")!!.toBoolean()

        val projectSpecs = projectSpecs()
        logMessage { "projectSpecs: $projectSpecs" }
        for (projectSpec in projectSpecs) {
            val projectName = projectSpec.name
            val projectPath = projectSpec.path

            val suiteName =
                listOfNotNull("allKtFilesIn", "emptyProfile".takeIf { emptyProfile }, projectName)
                    .joinToString(separator = "-")
            suite(suiteName = suiteName) {
                app {
                    ExpressionsOfTypeProcessor.prodMode()
                    warmUpProject()

                    with(config) {
                        warmup = warmUpIterations
                        iterations = numberOfIterations
                        fastIterations = true
                    }

                    try {
                        project(ExternalProject.autoOpenProject(projectPath), refresh = true) {
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
                                FileTypeIndex.processFiles(
                                    KotlinFileType.INSTANCE,
                                    ktFileProcessor,
                                    GlobalSearchScope.projectScope(project)
                                )
                                modules
                            }

                            logStatValue("number of kt files", ktFiles.size)
                            val filesToProcess =
                                limitedFiles(
                                    ktFiles,
                                    percentOfFiles = percentOfFiles,
                                    maxFilesPerPart = maxFilesPerPart,
                                    minFileSize = minFileSize
                                )
                            logStatValue("limited number of kt files", filesToProcess.size)

                            filesToProcess.forEach {
                                logMessage { "${project.relativePath(it)} fileSize: ${Files.size(it.toNioPath())}" }
                            }

                            filesToProcess.forEachIndexed { idx, file ->
                                logMessage { "${idx + 1} / ${filesToProcess.size} : ${project.relativePath(file)} fileSize: ${Files.size(file.toNioPath())}" }

                                try {
                                    fixture(file).use {
                                        measure<List<HighlightInfo>>(it.fileName, clearCaches = clearPsiCaches) {
                                            test = {
                                                it.highlight()
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    // nothing as it is already caught by perfTest
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // nothing as it is already caught by perfTest
                    }
                }
            }
        }
    }

    private fun limitedFiles(
        ktFiles: Collection<VirtualFile>,
        percentOfFiles: Int,
        maxFilesPerPart: Int,
        minFileSize: Int
    ): Collection<VirtualFile> {
        val sortedBySize = ktFiles
            .filter { Files.size(it.toNioPath()) > minFileSize }
            .map { it to Files.size(it.toNioPath()) }.sortedByDescending { it.second }
        val numberOfFiles = minOf((sortedBySize.size * percentOfFiles) / 100, maxFilesPerPart)

        val topFiles = sortedBySize.take(numberOfFiles).map { it.first }
        val midFiles =
            sortedBySize.take(sortedBySize.size / 2 + numberOfFiles / 2).takeLast(numberOfFiles).map { it.first }
        val lastFiles = sortedBySize.takeLast(numberOfFiles).map { it.first }

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
