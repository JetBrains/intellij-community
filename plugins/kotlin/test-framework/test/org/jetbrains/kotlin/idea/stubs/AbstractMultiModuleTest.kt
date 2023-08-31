// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubs

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.idea.facet.getOrCreateFacet
import org.jetbrains.kotlin.idea.facet.initializeIfNeeded
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.allowProjectRootAccess
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.disposeVfsRootAccess
import org.jetbrains.kotlin.idea.test.util.slashedPath
import org.jetbrains.kotlin.platform.TargetPlatform
import org.junit.Assert
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

abstract class AbstractMultiModuleTest : DaemonAnalyzerTestCase() {
    open fun isFirPlugin(): Boolean = false

    private var vfsDisposable: Ref<Disposable>? = null

    abstract fun getTestDataDirectory(): File

    final override fun getTestDataPath(): String {
        return getTestDataDirectory().slashedPath
    }

    override fun setUp() {
        super.setUp()
        enableKotlinOfficialCodeStyle(project)

        vfsDisposable = allowProjectRootAccess(this)
        checkPluginIsCorrect(isFirPlugin())
    }

    fun module(name: String, hasTestRoot: Boolean = false, sdkFactory: () -> Sdk = { IdeaTestUtil.getMockJdk18() }): Module {
        val srcDir = testDataPath + "${getTestName(true)}/$name"
        val moduleWithSrcRootSet = createModuleFromTestData(srcDir, name, StdModuleTypes.JAVA, true)
        if (hasTestRoot) {
            addRoot(
                moduleWithSrcRootSet,
                File(testDataPath + "${getTestName(true)}/${name}Test"),
                true
            )
        }

        ConfigLibraryUtil.configureSdk(moduleWithSrcRootSet, PluginTestCaseBase.addJdk(testRootDisposable, sdkFactory))

        return moduleWithSrcRootSet
    }

    protected fun createModuleInTmpDir(
        name: String,
        createFiles: () -> List<FileWithText> = { emptyList() },
    ): Module {
        val tmpDir = createTempDirectory().toPath()
        val root = (tmpDir / name).createDirectory()
        val src1 = (root / "src").createDirectory()
        createFiles().forEach { file ->
            (src1 / file.name).writeText(file.text)
        }
        val module: Module = createModule(root, moduleType)
        module.addContentRoot(src1)
        return module
    }

    protected fun Module.addContentRoot(rootPath: Path): SourceFolder {
        val root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(rootPath.toFile())!!
        WriteCommandAction.writeCommandAction(module.project).run<RuntimeException> {
            root.refresh(false, true)
        }

        return PsiTestUtil.addSourceContentToRoots(this, root)
    }

    protected data class FileWithText(val name: String, val text: String)


    override fun tearDown() {
        runAll(
            ThrowableRunnable { disposeVfsRootAccess(vfsDisposable) },
            ThrowableRunnable { disableKotlinOfficialCodeStyle(project) },
            ThrowableRunnable { super.tearDown() }
        )
    }

    public override fun createModule(path: String, moduleType: ModuleType<*>): Module {
        return super.createModule(path, moduleType)
    }

    fun VirtualFile.sourceIOFile(): File? = getUserData(sourceIOFile)

    fun addRoot(module: Module, sourceDirInTestData: File, isTestRoot: Boolean, transformContainedFiles: ((File) -> Unit)? = null) {
        val tmpDir = createTempDirectory()

        // Preserve original root name. This might be useful for later matching of copied files to original ones
        val tmpRootDir = File(tmpDir, sourceDirInTestData.name).also { it.mkdir() }

        FileUtil.copyDir(sourceDirInTestData, tmpRootDir)

        if (transformContainedFiles != null) {
            tmpRootDir.listFiles().forEach(transformContainedFiles)
        }

        val virtualTempDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tmpRootDir)!!
        virtualTempDir.putUserData(sourceIOFile, sourceDirInTestData)
        virtualTempDir.refresh(false, isTestRoot)
        PsiTestUtil.addSourceRoot(module, virtualTempDir, isTestRoot)
    }

    fun Module.addDependency(
        other: Module,
        dependencyScope: DependencyScope = DependencyScope.COMPILE,
        exported: Boolean = false
    ): Module = this.apply { ModuleRootModificationUtil.addDependency(this, other, dependencyScope, exported) }

    fun Module.removeDependency(
        other: Module,
    ): Module = this.apply {
        ModuleRootModificationUtil.updateModel(this) { model ->
            val entry = model.orderEntries
                .filterIsInstance<ModuleOrderEntry>()
                .filter { it.moduleName == other.name }
                .single()
            model.removeOrderEntry(entry)
        }
    }

    fun Module.addLibrary(
        jar: File,
        name: String = KotlinJdkAndLibraryProjectDescriptor.LIBRARY_NAME,
        kind: PersistentLibraryKind<*>? = null
    ) = addMultiJarLibrary(listOf(jar), name, kind)

    fun Module.addMultiJarLibrary(
        jars: Collection<File>,
        name: String = KotlinJdkAndLibraryProjectDescriptor.LIBRARY_NAME,
        kind: PersistentLibraryKind<*>? = null,
    ) {
        assert(jars.isNotEmpty()) { "No JARs passed for a library" }
        ConfigLibraryUtil.addLibrary(this, name, kind) {
            for (jar in jars) {
                addRoot(jar, OrderRootType.CLASSES)
            }
        }
    }

    fun Module.enableMultiPlatform(additionalCompilerArguments: String = "") {
        createFacet()
        val facetSettings = KotlinFacetSettingsProvider.getInstance(project)?.getInitializedSettings(this)
            ?: error("Facet settings are not found")

        facetSettings.useProjectSettings = false
        facetSettings.compilerSettings = CompilerSettings().apply {
            additionalArguments += " -Xmulti-platform -Xexpect-actual-classes $additionalCompilerArguments"
        }
    }

    fun Module.enableCoroutines() {
        createFacet()
        val facetSettings = KotlinFacetSettingsProvider.getInstance(project)?.getInitializedSettings(this)
            ?: error("Facet settings are not found")

        facetSettings.useProjectSettings = false
    }

    protected fun checkFiles(
        findFiles: () -> List<PsiFile>,
        check: () -> Unit
    ) {
        var atLeastOneFile = false
        findFiles().forEach { file ->
            configureByExistingFile(file.virtualFile!!)
            atLeastOneFile = true
            check()
        }
        Assert.assertTrue(atLeastOneFile)
    }
}

private val sourceIOFile: Key<File> = Key("sourceIOFile")

fun Module.createFacet(
    platformKind: TargetPlatform? = null,
    useProjectSettings: Boolean = true
) {
    createFacetWithAdditionalSetup(platformKind, useProjectSettings) { }
}

fun Module.createMultiplatformFacetM1(
    platformKind: TargetPlatform? = null,
    useProjectSettings: Boolean = true,
    implementedModuleNames: List<String>,
    pureKotlinSourceFolders: List<String>,
    additionalVisibleModuleNames: Set<String>
) {
    createFacetWithAdditionalSetup(platformKind, useProjectSettings) {
        this.implementedModuleNames = implementedModuleNames
        this.pureKotlinSourceFolders = pureKotlinSourceFolders
        this.additionalVisibleModuleNames = additionalVisibleModuleNames
    }
}

fun Module.createMultiplatformFacetM3(
    platformKind: TargetPlatform? = null,
    useProjectSettings: Boolean = true,
    dependsOnModuleNames: List<String>,
    pureKotlinSourceFolders: List<String>
) {
    createFacetWithAdditionalSetup(platformKind, useProjectSettings) {
        this.dependsOnModuleNames = dependsOnModuleNames
        this.isHmppEnabled = true
        this.pureKotlinSourceFolders = pureKotlinSourceFolders
    }
}

private fun Module.createFacetWithAdditionalSetup(
    platformKind: TargetPlatform?,
    useProjectSettings: Boolean,
    additionalSetup: KotlinFacetSettings.() -> Unit
) {
    WriteAction.run<Throwable> {
        val modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(project)
        with(getOrCreateFacet(modelsProvider, useProjectSettings).configuration.settings) {
            initializeIfNeeded(
                this@createFacetWithAdditionalSetup,
                modelsProvider.getModifiableRootModel(this@createFacetWithAdditionalSetup),
                platformKind
            )
            additionalSetup()
        }
        modelsProvider.commit()
    }
}
