// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.isFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.MultiFileTestCase
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.*
import java.io.File

abstract class KotlinMultiFileTestCase : MultiFileTestCase(),
                                         ExpectedPluginModeProvider {

    protected var isMultiModule = false
    private var vfsDisposable: Ref<Disposable>? = null

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
        vfsDisposable = allowProjectRootAccess(this)

        runWriteAction {
            val mockJdk16 = IdeaTestUtil.getMockJdk16()
            PluginTestCaseBase.addJdk(testRootDisposable) { mockJdk16 }
            ProjectRootManager.getInstance(project).projectSdk = mockJdk16
        }
    }

    protected open fun fileFilter(file: VirtualFile): Boolean {
        if (pluginMode == KotlinPluginMode.K2) {
            if (file.name.endsWith(".k2.kt")) return true
            val k2CounterPart = file.parent.findChild("${file.nameWithoutExtension}.k2.kt")
            if (k2CounterPart?.isFile == true) return false
        } else {
            if (file.name.endsWith(".k2.kt")) return false
        }
        return !isMultiExtensionName(file.name)
    }

    protected open fun fileNameMapper(file: VirtualFile): String {
        return if (pluginMode == KotlinPluginMode.K2) {
            file.name.replace(".k2.kt", ".kt")
        } else file.name
    }

    override fun compareResults(rootAfter: VirtualFile, rootDir: VirtualFile) {
        PlatformTestUtil.assertDirectoriesEqual(rootAfter, rootDir, ::fileFilter, ::fileNameMapper)
    }

    final override fun getTestDataPath(): String {
        return toSlashEndingDirPath(getTestDataDirectory().absolutePath)
    }

    protected open fun getTestDataDirectory(): File {
        return File(super.getTestDataPath())
    }

    protected fun getTestDirName(lowercaseFirstLetter: Boolean): String {
        val testName = getTestName(lowercaseFirstLetter)
        val endIndex = testName.lastIndexOf('_')
        if (endIndex < 0) return testName
        return testName.substring(0, endIndex).replace('_', '/')
    }

    protected fun doTestCommittingDocuments(action: (VirtualFile, VirtualFile?) -> Unit) {
        super.doTest(
            { rootDir, rootAfter ->
                action(rootDir, rootAfter)

                PsiDocumentManager.getInstance(project!!).commitAllDocuments()
                FileDocumentManager.getInstance().saveAllDocuments()
            }, getTestDirName(true)
        )
    }

    override fun prepareProject(rootDir: VirtualFile) {
        if (isMultiModule) {
            val model = ModuleManager.getInstance(project).getModifiableModel()

            VfsUtilCore.visitChildrenRecursively(
                rootDir,
                object : VirtualFileVisitor<Any>() {
                    override fun visitFile(file: VirtualFile): Boolean {
                        if (!file.isDirectory && file.name.endsWith(ModuleFileType.DOT_DEFAULT_EXTENSION)) {
                            model.loadModule(file.path)
                            return false
                        }

                        return true
                    }
                }
            )

            runWriteAction { model.commit() }
            IndexingTestUtil.waitUntilIndexesAreReady(project)
        } else {
            PsiTestUtil.addSourceContentToRoots(myModule, rootDir)
        }
    }

    override fun tearDown() {
        runAll(
            { disposeVfsRootAccess(vfsDisposable) },
            { super.tearDown() },
        )
    }
}