// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiElement
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestUtil.doTestProvider
import com.intellij.util.PsiNavigateUtil
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.junit.jupiter.api.BeforeEach
import java.io.File

abstract class AbstractKotlinInlayHintsProviderTest {
    protected val myFixture: CodeInsightTestFixture by codeInsightModuleFixture(projectFixture, descriptor.moduleTypeId)

    @TestDisposable
    lateinit var disposable: Disposable

    @BeforeEach
    fun setUpKotlinModule() {
        val module = myFixture.module
        val sdk = descriptor.sdk!!
        WriteAction.runAndWait<RuntimeException> { ProjectJdkTable.getInstance().addJdk(sdk, disposable) }
        ModuleRootModificationUtil.setModuleSdk(module, sdk)
        ModuleRootModificationUtil.updateModel(module) { descriptor.configureModule(module, it, it.contentEntries.single()) }
        IndexingTestUtil.waitUntilIndexesAreReady(myFixture.project)
    }

    private val customToStringProvider: (PsiElement) -> String = provider@{ element ->
        val navigatable = PsiNavigateUtil.getNavigatable(element)
        val openFileDescriptor = navigatable as? OpenFileDescriptor ?: return@provider ""
        val virtualFile = openFileDescriptor.file
        val jarFileSystem = virtualFile.fileSystem as? JarFileSystem
        val path = jarFileSystem?.let {
            val root = VfsUtilCore.getRootFile(virtualFile)
            "${it.protocol}://${root.name}${JarFileSystem.JAR_SEPARATOR}${VfsUtilCore.getRelativeLocation(virtualFile, root)}"
        } ?: virtualFile.name
        "[$path:${if (jarFileSystem != null) "*" else openFileDescriptor.offset.toString()}]"
    }

    /**
     * Runs [doTest] for [testDataFilePath] relative to the `@TestRoot` of the generated class.
     * The generated `@Test` methods call it.
     */
    protected fun runTest(testDataFilePath: String) {
        val testRoot = TestMetadataUtil.getTestRoot(javaClass) ?: error("@TestRoot annotation was not found on ${javaClass.name}")
        doTest(File(testRoot, testDataFilePath).path)
    }

    open fun doTest(testPath: String) {
        val file = File(testPath)
        configureDependencies(file)
        IgnoreTests.runTestIfNotDisabledByFileDirective(file.toPath(), IgnoreTests.DIRECTIVES.IGNORE_K2) {
            doTestProviders(file = file)
        }
    }

    private val dependencySuffixes = listOf(".dependency.kt", ".dependency.java", ".dependency1.kt", ".dependency2.kt")

    protected fun configureDependencies(file: File) {
        val parentFile = file.parentFile
        File(parentFile, "_lib.kt").configureFixture()
        for (suffix in dependencySuffixes) {
            val dependency = file.name.replace(".kt", suffix)
            File(parentFile, dependency).configureFixture()
        }
    }

    private fun File.configureFixture() {
        if (this.exists()) {
            // The test data path of the fixture is not a parent of the file, so give the target path explicitly.
            myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(this.absolutePath, this.name))
        }
    }

    protected abstract fun inlayHintsProvider(): InlayHintsProvider

    protected open fun calculateOptions(fileContents: String): Map<String, Boolean> =
        emptyMap()

    protected open fun doTestProviders(file: File) {
        val fileContents: String = FileUtil.loadFile(file, true)
        val inlayHintsProvider: InlayHintsProvider = inlayHintsProvider()
        val options: Map<String, Boolean> = calculateOptions(fileContents)

        try {
            withCustomCompilerOptions(fileContents, myFixture.project, myFixture.module) {
                doTestProvider(myFixture, "${file.name.substringBefore(".")}.kt", fileContents, inlayHintsProvider, options, file,
                               customToStringProvider = customToStringProvider)
            }
        } catch (e: FileComparisonFailedError) {
            throw FileComparisonFailedError(
                e.message,
                e.expectedStringPresentation,
                e.actualStringPresentation,
                file.absolutePath,
                null
            )
        }
    }

    companion object {
        private val projectFixture = projectFixture(openAfterCreation = true)
        private val descriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }
}