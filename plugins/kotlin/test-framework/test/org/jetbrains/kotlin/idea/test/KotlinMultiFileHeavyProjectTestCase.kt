// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.HeavyTestHelper
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.io.directoryContent
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import java.nio.file.Path
import kotlin.io.path.*

abstract class KotlinMultiFileHeavyProjectTestCase : HeavyPlatformTestCase(),
                                                     ExpectedPluginModeProvider {

    open val testDataDirectory: Path by lazy {
        Path(TestMetadataUtil.getTestDataPath(javaClass))
    }

    protected open fun fileName(): String = KotlinTestUtils.getTestDataFileName(this::class.java, this.name) ?: (getTestName(false) + ".kt")
    protected open fun mainFile(): Path = testDataDirectory.resolve(fileName())

    override fun getTestProjectJdk(): Sdk? = IdeaTestUtil.getMockJdk11()

    override fun getModuleType(): ModuleType<*> = JavaModuleType.getModuleType()

    private var mockLibraryFacility: MockLibraryFacility? = null

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    override fun tearDown(): Unit = runAll(
        { mockLibraryFacility?.tearDown(module) },
        { super.tearDown() },
    )

    protected open fun doTest(testDataPath: String) {
        doMultiFileTest(testDataPath)
    }

    private fun doMultiFileTest(testDataPath: String) {
        val mainFile = Path(testDataPath)
        val subFiles: List<KotlinBaseTest.TestFile> = createTestFiles(mainFile)
        val rootPath = tempDir.newPath()
        directoryContent {
            dir("src") {}
        }.generate(rootPath)

        val srcPath = rootPath.resolve("src")
        configureMultiFileTest(subFiles, srcPath)
        HeavyTestHelper.createTestProjectStructure(module, srcPath.pathString, srcPath, true)

        val libraryFile = Path("$testDataPath.lib")
        if (libraryFile.exists()) {
            val libraryPath = rootPath.resolve("lib")
            configureLibraries(libraryFile, libraryPath)
        }

        val globalDirectives = subFiles.firstOrNull()?.directives ?: Directives()
        if ("WITH_STDLIB" in globalDirectives) {
            runWriteAction {
                val library = LibraryTablesRegistrar.getInstance().getLibraryTable(project).createLibrary("kotlin-stdlib")
                with(library.modifiableModel) {
                    addRoot(TestKotlinArtifacts.kotlinStdlib, OrderRootType.CLASSES)
                    addRoot(TestKotlinArtifacts.kotlinStdlibCommon, OrderRootType.CLASSES)

                    if ("WITH_SOURCES" in globalDirectives) {
                        addRoot(TestKotlinArtifacts.kotlinStdlibSources, OrderRootType.SOURCES)
                        addRoot(TestKotlinArtifacts.kotlinStdlibCommonSources, OrderRootType.SOURCES)
                    }

                    commit()
                }

                ModuleRootManager.getInstance(module).modifiableModel.apply {
                    addLibraryEntry(library)
                    commit()
                }
            }
        }

        IndexingTestUtil.waitUntilIndexesAreReady(project)
        doMultiFileTest(testDataPath, globalDirectives)
    }

    private fun configureLibraries(libraryFile: Path, targetPath: Path) {
        val libraryFiles = createTestFiles(libraryFile)
        assertNotEmpty(libraryFiles)

        val directives = libraryFiles.first().directives
        val compilerArguments = directives["COMPILER_ARGUMENTS"].orEmpty()

        val sourcesPath = targetPath.resolve("sources")
        for (testFile in libraryFiles) {
            val path = sourcesPath.resolve(testFile.name)
            path.parent.createDirectories()

            val newFile = path.createFile()
            newFile.writeText(testFile.content)
        }

        val libraryName = directives["LIBRARY_NAME"] ?: MockLibraryFacility.MOCK_LIBRARY_NAME
        val jarFile = targetPath.resolve("$libraryName.jar").createFile().toFile()

        mockLibraryFacility = MockLibraryFacility(
            sources = listOf(sourcesPath.toFile()),
            attachSources = "WITH_SOURCES" in directives,
            libraryName = libraryName,
            target = jarFile,
            options = compilerArguments.split(' ').map(String::trim).filter(String::isNotBlank),
        )

        mockLibraryFacility?.setUp(module)
    }

    private fun createTestFiles(mainFile: Path): List<KotlinBaseTest.TestFile> = TestFiles.createTestFiles(
        /* testFileName = */ mainFile.name.takeIf { it.endsWith("kt") || it.endsWith("kts") } ?: "single.kt",
        /* expectedText = */ mainFile.readText(),
        object : TestFiles.TestFileFactoryNoModules<KotlinBaseTest.TestFile>() {
            override fun create(fileName: String, text: String, directives: Directives): KotlinBaseTest.TestFile {
                return KotlinBaseTest.TestFile(fileName, text, directives)
            }
        },
    )

    protected open fun doMultiFileTest(testDataPath: String, globalDirectives: Directives) {
        throw UnsupportedOperationException()
    }

    private fun configureMultiFileTest(subFiles: List<KotlinBaseTest.TestFile>, contentPath: Path) {
        for (file in subFiles) {
            val newFile = contentPath.resolve(file.name)
            newFile.parent.createDirectories()
            newFile.writeText(file.content)
        }

        VfsUtil.findFile(contentPath, true)!!.refresh(false, true)
    }

    open fun isFirPlugin(): Boolean = false
}