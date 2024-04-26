// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.performance.tests.utils

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.lang.LanguageAnnotators
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.editor.Document
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.classes.runReadAction
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import java.nio.file.Paths

fun commitAllDocuments() {
    val fileDocumentManager = FileDocumentManager.getInstance()
    runInEdtAndWait {
        fileDocumentManager.saveAllDocuments()
    }

    ProjectManagerEx.getInstanceEx().openProjects.forEach { project ->
        val psiDocumentManagerBase = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase

        runInEdtAndWait {
            psiDocumentManagerBase.clearUncommittedDocuments()
            psiDocumentManagerBase.commitAllDocuments()
        }
    }
}

fun commitDocument(project: Project, document: Document) {
    val psiDocumentManagerBase = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase
    runInEdtAndWait {
        psiDocumentManagerBase.commitDocument(document)
    }
}

fun saveDocument(document: Document) {
    val fileDocumentManager = FileDocumentManager.getInstance()

    runInEdtAndWait {
        fileDocumentManager.saveDocument(document)
    }
}

fun dispatchAllInvocationEvents() {
    runInEdtAndWait {
        UIUtil.dispatchAllInvocationEvents()
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    }
}

fun loadProjectWithName(path: String, name: String): Project? {
    return ProjectManagerEx.getInstanceEx().openProject(Paths.get(path), OpenProjectTask { this.projectName = name })
}

fun TestApplicationManager.closeProject(project: Project) {
    val name = project.name
    val startupManagerImpl = StartupManager.getInstance(project) as StartupManagerImpl
    val daemonCodeAnalyzerSettings = DaemonCodeAnalyzerSettings.getInstance()
    val daemonCodeAnalyzerImpl = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl

    setDataProvider(null)
    daemonCodeAnalyzerSettings.isImportHintEnabled = true // return default value to avoid unnecessary save
    startupManagerImpl.checkCleared()
    daemonCodeAnalyzerImpl.cleanupAfterTest()

    logMessage { "project '$name' is about to be closed" }
    dispatchAllInvocationEvents()
    runInEdtAndWait { ProjectManagerEx.getInstanceEx().forceCloseProject(project) }

    logMessage { "project '$name' successfully closed" }
}

fun replaceWithCustomHighlighter(parentDisposable: Disposable, fromImplementationClass: String, toImplementationClass: String) {
    val pointName = ExtensionPointName.create<LanguageExtensionPoint<Annotator>>(LanguageAnnotators.EP_NAME.name)
    val extensionPoint = pointName.getPoint(null)

    val point = LanguageExtensionPoint<Annotator>()
    point.language = "kotlin"
    point.implementationClass = toImplementationClass

    val extensions = extensionPoint.extensions
    val filteredExtensions =
        extensions.filter { it.language != "kotlin" || it.implementationClass != fromImplementationClass }
            .toList()
    // custom highlighter is already registered if filteredExtensions has the same size as extensions
    if (filteredExtensions.size < extensions.size) {
        ExtensionTestUtil.maskExtensions(pointName, filteredExtensions + listOf(point), parentDisposable)
    }
}

fun projectFileByName(project: Project, name: String): PsiFile {
    fun baseName(name: String): String {
        val index = name.lastIndexOf("/")
        return if (index > 0) name.substring(index + 1) else name
    }

    val fileManager = VirtualFileManager.getInstance()
    val url = "${project.guessProjectDir()}/$name"
    fileManager.refreshAndFindFileByUrl(url)?.let {
        return it.toPsiFile(project)!!
    }

    val baseFileName = baseName(name)
    val projectBaseName = baseName(project.name)

    val virtualFiles = runReadAction {
        FilenameIndex.getVirtualFilesByName(
            baseFileName, true, GlobalSearchScope.projectScope(project)
        )
    }

    TestCase.assertEquals(
        "expected the only file with name '$name'\n, it were: [${virtualFiles.map { it.canonicalPath }.joinToString("\n")}]",
        1,
        virtualFiles.size
    )
    return runReadAction { virtualFiles.iterator().next().toPsiFile(project)!! }
}

fun Project.relativePath(file: VirtualFile): String {
    val basePath = guessProjectDir() ?: error("don't use it for a default project $this")
    return FileUtil.getRelativePath(basePath.toNioPath().toFile(), file.toNioPath().toFile())
        ?: error("$file is not located within a project $this")
}