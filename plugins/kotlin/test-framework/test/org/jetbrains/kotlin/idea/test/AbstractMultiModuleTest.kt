// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.facet.getOrCreateFacet
import org.jetbrains.kotlin.idea.facet.initializeIfNeeded
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.allowProjectRootAccess
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.disposeVfsRootAccess
import org.jetbrains.kotlin.idea.test.util.slashedPath
import org.jetbrains.kotlin.idea.util.sourceRoots
import org.jetbrains.kotlin.konan.target.TargetSupportException
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.div
import kotlin.io.path.writeText

abstract class AbstractMultiModuleTest : DaemonAnalyzerTestCase(),
                                         ExpectedPluginModeProvider {

    private var vfsDisposable: Ref<Disposable>? = null

    abstract fun getTestDataDirectory(): File

    final override fun getTestDataPath(): String {
        return getTestDataDirectory().slashedPath
    }

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
        enableKotlinOfficialCodeStyle(project)

        vfsDisposable = allowProjectRootAccess(this)
    }

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1

    // [TargetSupportException] can be thrown by the multiplatform test setup when a test artifact doesn't exist for the host platform.
    // The test should be ignored in such cases, but since JUnit3 doesn't provide such an option, we make them pass instead.
    // If KT-36871 is fixed and no platform-specific artifacts are used in tests, this hack should be removed.
    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        try {
            super.runTestRunnable(testRunnable)
        } catch (e: TargetSupportException) {
            LOG.warn(e)
        }
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
            { disposeVfsRootAccess(vfsDisposable) },
            { disableKotlinOfficialCodeStyle(project) },
            { super.tearDown() },
        )
    }

    public override fun createModule(path: String, moduleType: ModuleType<*>): Module {
        return super.createModule(path, moduleType)
    }

    fun VirtualFile.sourceIOFile(): File? = getUserData(sourceIOFile)

    fun VirtualFile.toIOFile(): File? {
        val paths = mutableListOf<String>()
        var vFile: VirtualFile? = this
        while (vFile != null) {
            vFile.sourceIOFile()?.let {
                return File(it, paths.reversed().joinToString("/"))
            }
            paths.add(vFile.name)
            vFile = vFile.parent
        }
        return null
    }

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
        exported: Boolean = false,
        productionOnTest: Boolean = false,
    ): Module = this.apply { ModuleRootModificationUtil.addDependency(this, other, dependencyScope, exported, productionOnTest) }

    fun Module.removeDependency(
        other: Module,
    ): Module = this.apply {
        ModuleRootModificationUtil.updateModel(this) { model ->
            val entry = model.orderEntries
                .filterIsInstance<ModuleOrderEntry>().single { it.moduleName == other.name }
            model.removeOrderEntry(entry)
        }
    }

    fun Module.addLibrary(
        jar: File,
        name: String = KotlinJdkAndLibraryProjectDescriptor.LIBRARY_NAME,
        kind: PersistentLibraryKind<*>? = null,
        sourceJar: File? = null
    ) = addMultiJarLibrary(listOf(jar), name, kind, listOfNotNull(sourceJar))

    fun Module.addMultiJarLibrary(
        jars: Collection<File>,
        name: String = KotlinJdkAndLibraryProjectDescriptor.LIBRARY_NAME,
        kind: PersistentLibraryKind<*>? = null,
        sourceJars: Collection<File> = emptyList(),
    ) {
        assert(jars.isNotEmpty()) { "No JARs passed for a library" }
        ConfigLibraryUtil.addLibrary(this, name, kind) {
            for (jar in jars) {
                addRoot(jar, OrderRootType.CLASSES)
            }
            for (sourceJar in sourceJars) {
                addRoot(sourceJar, OrderRootType.SOURCES)
            }
        }
    }

    fun Module.addLibrary(
        file: VirtualFile,
        name: String = KotlinJdkAndLibraryProjectDescriptor.LIBRARY_NAME,
        kind: PersistentLibraryKind<*>? = null,
        sourceFile: VirtualFile? = null,
    ) {
        ConfigLibraryUtil.addLibrary(this, name, kind) {
            addRoot(file, OrderRootType.CLASSES)
            sourceFile?.let { addRoot(it, OrderRootType.SOURCES) }
        }
    }

    fun Module.addKotlinStdlib() = runWriteAction {
        val modifiableModel = rootManager.modifiableModel
        KotlinWithJdkAndRuntimeLightProjectDescriptor.JDK_AND_RUNTIME_LIGHT_PROJECT_DESCRIPTOR.configureModule(
            this,
            modifiableModel,
        )
        modifiableModel.commit()
    }

    fun Module.findSourceKtFile(fileName: String): KtFile {
        val file = "${sourceRoots.first().url}/$fileName"
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl(file)!!
        return PsiManager.getInstance(myProject).findFile(virtualFile) as KtFile
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

    protected fun createKtFileUnderNewContentRoot(fileWithText: FileWithText): KtFile {
        val tmpDir = createTempDirectory().toPath()

        // We need to add the script to a module so that it's part of the project's content root.
        val containingModule = createModule(tmpDir, moduleType)
        PsiTestUtil.addContentRoot(containingModule, getVirtualFile(tmpDir.toFile()))

        val filePath = tmpDir / fileWithText.name
        filePath.writeText(fileWithText.text)

        val file = getVirtualFile(filePath.toFile())
        return file.toPsiFile(project)!! as KtFile
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
    dependsOnModuleNames: List<String> = emptyList(),
    pureKotlinSourceFolders: List<String> = emptyList(),
    additionalVisibleModuleNames: Set<String> = emptySet(),
    isHmppEnabled: Boolean = true
) {
    createFacetWithAdditionalSetup(platformKind, useProjectSettings) {
        this.dependsOnModuleNames = dependsOnModuleNames
        this.additionalVisibleModuleNames = additionalVisibleModuleNames
        this.isHmppEnabled = isHmppEnabled
        this.pureKotlinSourceFolders = pureKotlinSourceFolders
    }
}

private fun Module.createFacetWithAdditionalSetup(
    platformKind: TargetPlatform?,
    useProjectSettings: Boolean,
    additionalSetup: IKotlinFacetSettings.() -> Unit
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
