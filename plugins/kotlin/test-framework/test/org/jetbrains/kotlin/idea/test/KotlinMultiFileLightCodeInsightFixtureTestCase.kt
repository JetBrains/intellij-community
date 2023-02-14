// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.test.KotlinBaseTest.TestFile
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

abstract class KotlinMultiFileLightCodeInsightFixtureTestCase : KotlinLightCodeInsightFixtureTestCase() {
    protected open val isLibraryByDefault: Boolean get() = false

    private var mockLibraryFacility: MockLibraryFacility? = null
    private var tempDirectory: Path? = null

    @OptIn(ExperimentalPathApi::class)
    override fun tearDown(): Unit = runAll(
        { mockLibraryFacility?.tearDown(module) },
        { tempDirectory?.deleteRecursively() },
        { super.tearDown() },
    )

    protected open fun doTest(testDataPath: String) {
        doMultiFileTest(testDataPath)
    }

    private fun doMultiFileTest(testDataPath: String) {
        val mainFile = Path(testDataPath)

        val subFiles: List<TestFile> = if (isLibraryByDefault) {
            emptyList()
        } else {
            createTestFiles(mainFile)
        }

        val libraryFile = Path(if (isLibraryByDefault) testDataPath else "$testDataPath.lib")
        if (libraryFile.exists()) {
            configureLibraries(libraryFile)
        }

        val files = configureMultiFileTest(subFiles)
        doMultiFileTest(
            testDataPath = testDataPath,
            files = files,
            globalDirectives = files.firstOrNull()?.testFile?.directives ?: Directives(),
        )
    }

    private fun configureLibraries(libraryFile: Path) {
        val libraryFiles = createTestFiles(libraryFile)
        assertNotEmpty(libraryFiles)

        val directives = libraryFiles.first().directives
        val compilerArguments = directives["COMPILER_ARGUMENTS"].orEmpty()

        val directoryForLibFiles = createTempDirectory(prefix = fileName()).also { tempDirectory = it }
        val sources = mutableListOf<File>()
        for (testFile in libraryFiles) {
            val path = directoryForLibFiles.resolve(testFile.name)
            path.parent.createDirectories()

            val newFile = path.createFile()
            newFile.writeText(testFile.content)
            sources += newFile.toFile()
        }

        val libraryName = directives["LIBRARY_NAME"] ?: MockLibraryFacility.MOCK_LIBRARY_NAME
        val jarFile = directoryForLibFiles.resolve("$libraryName.jar").createFile().toFile()

        mockLibraryFacility = MockLibraryFacility(
            sources = sources,
            attachSources = "WITH_SOURCES" in directives,
            libraryName = libraryName,
            target = jarFile,
            options = compilerArguments.split(' ').map(String::trim).filter(String::isNotBlank),
        )

        mockLibraryFacility?.setUp(module)
    }

    private fun createTestFiles(mainFile: Path): List<TestFile> = TestFiles.createTestFiles(
        /* testFileName = */ mainFile.name.takeIf { it.endsWith("kt") || it.endsWith("kts") } ?: "single.kt",
        /* expectedText = */ mainFile.readText(),
        object : TestFiles.TestFileFactoryNoModules<TestFile>() {
            override fun create(fileName: String, text: String, directives: Directives): TestFile {
                return TestFile(fileName, text, directives)
            }
        },
    )

    protected open fun doMultiFileTest(testDataPath: String, files: List<TestFileWithVirtualFile>, globalDirectives: Directives) {
        val psiFiles = files.mapNotNull { it.virtualFile.toPsiFile(project) }
        doMultiFileTest(psiFiles, globalDirectives)
    }

    protected open fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        throw UnsupportedOperationException()
    }

    private fun configureMultiFileTest(subFiles: List<TestFile>): List<TestFileWithVirtualFile> {
        return subFiles.map { TestFileWithVirtualFile(it, this.createTestFile(it)) }
    }

    private fun createTestFile(testFile: TestFile): VirtualFile {
        return myFixture.tempDirFixture.createFile(testFile.name, testFile.content)
    }

    protected data class TestFileWithVirtualFile(val testFile: TestFile, val virtualFile: VirtualFile)
}