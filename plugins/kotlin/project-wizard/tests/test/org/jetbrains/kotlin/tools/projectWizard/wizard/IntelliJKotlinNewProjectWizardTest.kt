// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.application.options.CodeStyle
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.JAVA
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.KOTLIN
import com.intellij.ide.projectWizard.NewProjectWizardTestCase
import com.intellij.ide.wizard.LanguageNewProjectWizardData.Companion.languageData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.java.library.getMavenCoordinates
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.RwLockHolder.runWriteAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkProvider
import com.intellij.openapi.externalSystem.util.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.withValue
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.assertEqualsToFile
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.useProject
import com.intellij.testFramework.utils.module.assertModules
import com.intellij.ui.UiInterceptors
import com.intellij.ui.UiInterceptors.UiInterceptor
import com.intellij.util.SystemProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.VersionView
import org.jetbrains.kotlin.config.apiVersionView
import org.jetbrains.kotlin.config.languageVersionView
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.formatter.KotlinObsoleteCodeStyle
import org.jetbrains.kotlin.idea.formatter.KotlinObsoleteStyleGuide
import org.jetbrains.kotlin.idea.formatter.KotlinOfficialStyleGuide
import org.jetbrains.kotlin.idea.formatter.ProjectCodeStyleImporter
import org.jetbrains.kotlin.idea.formatter.kotlinCodeStyleDefaults
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.test.TestMetadataUtil.getTestRoot
import org.jetbrains.kotlin.test.util.addDependency
import org.jetbrains.kotlin.tools.projectWizard.IntelliJKotlinNewProjectWizardData.Companion.kotlinData
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.StdlibVersionChooserDialog
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.rules.TestName
import java.io.File
import java.net.URL

@TestRoot("project-wizard/tests")
class IntelliJKotlinNewProjectWizardTest : NewProjectWizardTestCase() {
    companion object {
        private const val ONBOARDING_TIPS_SEARCH_STR = "with your caret at the highlighted text"
    }

    @Rule
    @JvmField
    val testName: TestName = TestName()

    private lateinit var mySdk: Sdk
    private lateinit var otherSdk: Sdk
    private lateinit var testDisposable: Disposable

    private lateinit var kotlinVersion: IdeKotlinVersion

    private fun copyTestData() {
        val testFolderPath = getTestDataFolder()
        testFolderPath.walkBottomUp().forEach { f ->
            if (!f.isFile || f.name.endsWith(".after")) return@forEach
            val relativePath = testFolderPath.toPath().relativize(f.toPath()).toString()
            val targetPath = contentRoot.resolve(relativePath)
            f.copyTo(targetPath)
        }
    }

    private fun getOutputFile(path: String): File {
        return File(contentRoot, path)
    }

    private fun assertCorrectContent() {
        val testFolderPath = getTestDataFolder()
        testFolderPath.walkBottomUp().forEach { f ->
            if (!f.isFile || !f.name.endsWith(".after")) return@forEach
            val outputName = f.name.removeSuffix(".after")
            val relativePath = testFolderPath.toPath().relativize(f.toPath()).toString().removeSuffix(".after")
            val pathInProject = File(contentRoot, relativePath)
            assertTrue(
                "Expected ${outputName} file to exist in output, but it could not be found.",
                pathInProject.exists() && pathInProject.isFile
            )
            assertEqualsToFile("Expected correct file after generation", f, pathInProject.readText())
        }
    }

    override fun setUp() {
        super.setUp()
        testDisposable = Disposer.newDisposable()
        kotlinVersion = IdeKotlinVersion.parse(Versions.KOTLIN.text).getOrThrow()
        copyTestData()

        mySdk = ExternalSystemJdkProvider.getInstance().internalJdk
        otherSdk = SimpleJavaSdkType().createJdk("_other", SystemProperties.getJavaHome())
        val jdkTable = ProjectJdkTable.getInstance()
        runWriteActionAndWait {
            jdkTable.addJdk(mySdk, testDisposable)
            jdkTable.addJdk(otherSdk, testDisposable)
        }
    }

    override fun tearDown() {
        runAll(
            // needed because creating Kotlin projects alters the code style
            { CodeStyle.getDefaultSettings().clearCodeStyleSettings() },
            { KotlinSdkType.removeKotlinSdkInTests() },
            { Disposer.dispose(testDisposable) },
            { super.tearDown() }
        )
    }

    private fun getTestFolderName(): String {
        return name.removePrefix("test").decapitalizeAsciiOnly()
    }

    private fun getTestDataFolder(): File {
        val testRoot = getTestRoot(IntelliJKotlinNewProjectWizardTest::class.java)
        return File(testRoot, "testData/jpsNewProjectWizard/${getTestFolderName()}")
    }

    private fun resolvePathInProject(path: String): File {
        return contentRoot.toPath().resolve(path.replace("/", File.separator)).toFile()
    }

    private fun NewProjectWizardStep.setWizardData(
        name: String,
        relativePath: String,
        addSampleCode: Boolean,
        useCompactProjectStructure: Boolean = true,
        generateOnboardingTips: Boolean = false,
        sdk: Sdk = mySdk
    ) {
        val languageData = languageData!!
        val kotlinData = kotlinData!!
        languageData.language = "Kotlin"
        languageData.name = name
        languageData.path = contentRoot.toPath().resolve(relativePath).toCanonicalPath()
        kotlinData.buildSystem = "IntelliJ"
        kotlinData.sdk = sdk
        kotlinData.addSampleCode = addSampleCode
        kotlinData.useCompactProjectStructure = useCompactProjectStructure
        kotlinData.generateOnboardingTips = generateOnboardingTips
    }

    private fun createProjectWithData(
        name: String = "project",
        addSampleCode: Boolean = false,
        useCompactProjectStructure: Boolean = true,
        generateOnboardingTips: Boolean = false
    ): Project {
        return createProjectFromTemplate(KOTLIN) {
            it.setWizardData(
                name = name,
                relativePath = "",
                addSampleCode = addSampleCode,
                useCompactProjectStructure = useCompactProjectStructure,
                generateOnboardingTips = generateOnboardingTips
            )
        }
    }

    private fun runProjectTestCase(
        name: String = "project",
        addSampleCode: Boolean = false,
        useCompactProjectStructure: Boolean = true,
        generateOnboardingTips: Boolean = false,
        f: (Project) -> Unit = { }
    ) {
        createProjectWithData(name, addSampleCode, useCompactProjectStructure, generateOnboardingTips).useProject { project ->
            assertCorrectContent()
            assertEquals(1, project.modules.size)
            project.assertHasKotlinJavaRuntime()
            assertModules(project, "project")
            project.modules.first().checkCorrectSourceRoots("project", useCompactProjectStructure)
            project.checkKotlinSettings()
            val mainModule = project.modules.first { it.name == "project" }
            assertEquals(mySdk, ProjectRootManager.getInstance(project).projectSdk)
            assertEquals(mySdk, ModuleRootManager.getInstance(mainModule).sdk)
            project.assertHasOfficialCodeStyle()
            f(project)
        }
    }

    private fun Project.findLibrary(name: String): Library? {
        val libraries = LibraryTablesRegistrar.getInstance().getLibraryTable(this)
        return libraries.libraries.singleOrNull { it.name.equals(name) }
    }

    private fun Project.assertDoesNotHaveKotlinJavaRuntime() {
        assertNull(findLibrary("KotlinJavaRuntime"))
    }

    private fun Project.assertHasKotlinJavaRuntime() {
        val kotlinRuntime = findLibrary("KotlinJavaRuntime")
        assertNotNull("Project should contain Kotlin runtime", kotlinRuntime)
        assertEquals("kotlin-stdlib", kotlinRuntime!!.getMavenCoordinates()?.artifactId)
    }

    private fun Project.assertHasOfficialCodeStyle(expected: Boolean = true) {
        val styleSettings = CodeStyle.getSettings(this)
        if (expected) {
            assertEquals(KotlinOfficialStyleGuide.CODE_STYLE_ID, styleSettings.kotlinCodeStyleDefaults())
        } else {
            assertNotEquals(KotlinOfficialStyleGuide.CODE_STYLE_ID, styleSettings.kotlinCodeStyleDefaults())
        }
    }

    private fun assertSamePath(expected: String, actual: String) {
        assertEquals(expected.replace("/", File.separator), actual.replace("/", File.separator))
    }

    private fun Project.checkKotlinSettings(
        expectedJvmTarget: String? = "1.8",
        expectedApiVersion: String? = kotlinVersion.apiVersion.toString(),
        expectedLanguageVersion: String? = kotlinVersion.languageVersion.toString()
    ) {
        val jvmSettings = Kotlin2JvmCompilerArgumentsHolder.getInstance(this).settings
        assertEquals(expectedJvmTarget, jvmSettings.jvmTarget)
        val commonSettings = KotlinCommonCompilerArgumentsHolder.getInstance(this).settings
        assertEquals(expectedApiVersion, commonSettings.apiVersion)
        assertEquals(expectedLanguageVersion, commonSettings.languageVersion)
    }

    private fun Project.updateKotlinSettings(
        jvmTarget: String,
        apiVersion: String,
        languageVersion: String
    ) {
        Kotlin2JvmCompilerArgumentsHolder.getInstance(this).update {
            this.jvmTarget = jvmTarget
        }
        KotlinCommonCompilerArgumentsHolder.getInstance(this).update {
            this.apiVersionView = VersionView.Specific(ApiVersion.parse(apiVersion)!!)
            this.languageVersionView = VersionView.Specific(ApiVersion.parse(languageVersion)!!)
        }
        checkKotlinSettings(jvmTarget, apiVersion, languageVersion)
    }

    private fun Project.createModuleWithData(
        name: String = "module",
        relativePath: String,
        addSampleCode: Boolean = false,
        useCompactProjectStructure: Boolean = true,
        generateOnboardingTips: Boolean = false,
        sdk: Sdk = mySdk
    ): Module {
        val module = createModuleFromTemplate(this, KOTLIN) {
            (it as NewProjectWizardStep).setWizardData(
                name = name,
                relativePath = relativePath,
                addSampleCode = addSampleCode,
                useCompactProjectStructure = useCompactProjectStructure,
                generateOnboardingTips = generateOnboardingTips,
                sdk = sdk
            )
        }
        return module
    }

    private fun Module.checkCorrectSourceRoots(pathToModule: String, useCompactProjectStructure: Boolean) {
        val contentEntries = ModuleRootManager.getInstance(this).contentEntries
        assertEquals(1, contentEntries.size)
        val sourceFolders = contentEntries.first().sourceFolders
        assertEquals(4, sourceFolders.size)
        val mainSource = sourceFolders.find { it.rootType == JavaSourceRootType.SOURCE }
        assertNotNull(mainSource)
        val resourceSource = sourceFolders.find { it.rootType == JavaResourceRootType.RESOURCE }
        assertNotNull(resourceSource)
        val testSource = sourceFolders.find { it.rootType == JavaSourceRootType.TEST_SOURCE }
        assertNotNull(testSource)
        val testResourceSource = sourceFolders.find { it.rootType == JavaResourceRootType.TEST_RESOURCE }
        assertNotNull(testResourceSource)
        if (useCompactProjectStructure) {
            assertTrue("src folder should exist", resolvePathInProject("$pathToModule/src").exists())
            assertSamePath(resolvePathInProject("$pathToModule/src").path, URL(mainSource!!.url).path)
            assertSamePath(resolvePathInProject("$pathToModule/resources").path, URL(resourceSource!!.url).path)
            assertSamePath(resolvePathInProject("$pathToModule/test").path, URL(testSource!!.url).path)
            assertSamePath(resolvePathInProject("$pathToModule/testResources").path, URL(testResourceSource!!.url).path)

            assertFalse("src/main folder should not exist", resolvePathInProject("$pathToModule/src/main").exists())
            assertFalse("src/test folder should not exist", resolvePathInProject("$pathToModule/src/test").exists())

            // These folders are not created when using the compact structure, only the JPS source roots are registered
            assertFalse("resources folder should not exist", resolvePathInProject("$pathToModule/resources").exists())
            assertFalse("test folder should not exist", resolvePathInProject("$pathToModule/test").exists())
            assertFalse("testResources folder should not exist", resolvePathInProject("$pathToModule/testResources").exists())
        } else {
            assertTrue("src folder should exist", resolvePathInProject("$pathToModule/src/main/kotlin").exists())
            assertTrue("test folder should exist", resolvePathInProject("$pathToModule/src/test/kotlin").exists())
            assertSamePath(resolvePathInProject("$pathToModule/src/main/kotlin").path, URL(mainSource!!.url).path)
            assertSamePath(resolvePathInProject("$pathToModule/src/main/resources").path, URL(resourceSource!!.url).path)
            assertSamePath(resolvePathInProject("$pathToModule/src/test/kotlin").path, URL(testSource!!.url).path)
            assertSamePath(resolvePathInProject("$pathToModule/src/test/resources").path, URL(testResourceSource!!.url).path)

            assertFalse("Java folder should not exist", resolvePathInProject("$pathToModule/src/main/java").exists())
            assertFalse("Java test folder should not exist", resolvePathInProject("$pathToModule/src/test/java").exists())
            assertFalse("compact resources folder should not exist", resolvePathInProject("$pathToModule/resources").exists())
            assertFalse("compact test folder should not exist", resolvePathInProject("$pathToModule/test").exists())
            assertFalse("compact testResources folder should not exist", resolvePathInProject("$pathToModule/testResources").exists())
        }
    }

    // New project tests

    fun testSimpleProject() {
        runProjectTestCase(addSampleCode = false, useCompactProjectStructure = false) {
            assertFalse("Sample file should not exist", resolvePathInProject("project/src/Main.kt").exists())
        }
    }

    fun testSimpleProjectCompact() {
        runProjectTestCase(addSampleCode = false, useCompactProjectStructure = true) {
            assertFalse("Sample file should not exist", resolvePathInProject("project/src/Main.kt").exists())
        }
    }

    fun testSampleCode() {
        runProjectTestCase(addSampleCode = true, useCompactProjectStructure = false) {
            assertTrue("Sample file should exist", resolvePathInProject("project/src/main/kotlin/Main.kt").exists())
        }
    }

    fun testSampleCodeCompact() {
        runProjectTestCase(addSampleCode = true, useCompactProjectStructure = true) {
            assertTrue("Sample file should exist", resolvePathInProject("project/src/Main.kt").exists())
        }
    }

    fun testOnboardingTips() {
        Registry.get("doc.onboarding.tips.render").withValue(false) {
            runProjectTestCase(addSampleCode = true, generateOnboardingTips = true, useCompactProjectStructure = false) { project ->
                val mainFile = getOutputFile("project/src/main/kotlin/Main.kt")
                assertTrue(mainFile.exists())
                val text = mainFile.readText()
                assertTrue(text.contains(ONBOARDING_TIPS_SEARCH_STR))
                assertFalse(text.contains("//TIP"))
            }
        }
    }

    fun testOnboardingTipsCompact() {
        Registry.get("doc.onboarding.tips.render").withValue(false) {
            runProjectTestCase(addSampleCode = true, generateOnboardingTips = true, useCompactProjectStructure = true) { project ->
                val mainFile = getOutputFile("project/src/Main.kt")
                assertTrue(mainFile.exists())
                val text = mainFile.readText()
                assertTrue(text.contains(ONBOARDING_TIPS_SEARCH_STR))
                assertFalse(text.contains("//TIP"))
            }
        }
    }

    fun testRenderedOnboardingTips() {
        Registry.get("doc.onboarding.tips.render").withValue(true) {
            runProjectTestCase(addSampleCode = true, generateOnboardingTips = true, useCompactProjectStructure = true) { project ->
                val mainFile = getOutputFile("project/src/Main.kt")
                assertTrue(mainFile.exists())
                val text = mainFile.readText()
                assertTrue(text.contains(ONBOARDING_TIPS_SEARCH_STR))
                assertTrue(text.contains("//TIP"))
            }
        }
    }

    // New module tests

    fun testSimpleNewModule() {
        createProjectWithData().useProject { project ->
            project.createModuleWithData("module", "project", useCompactProjectStructure = false)
            project.assertHasKotlinJavaRuntime()
            project.assertHasOfficialCodeStyle()
            assertCorrectContent()
            assertModules(project, "project", "module")
            project.checkKotlinSettings()

            project.modules.first { it.name == "module" }.checkCorrectSourceRoots("project/module", useCompactProjectStructure = false)
        }
    }

    fun testSimpleNewModuleCompact() {
        createProjectWithData().useProject { project ->
            project.createModuleWithData("module", "project", useCompactProjectStructure = true)
            project.assertHasKotlinJavaRuntime()
            project.assertHasOfficialCodeStyle()
            project.checkKotlinSettings()
            assertCorrectContent()
            assertModules(project, "project", "module")

            project.modules.first { it.name == "module" }.checkCorrectSourceRoots("project/module", useCompactProjectStructure = true)
        }
    }

    fun testNewModuleSampleCode() {
        createProjectWithData().useProject { project ->
            project.createModuleWithData("module", "project", addSampleCode = true)
            project.assertHasKotlinJavaRuntime()
            project.assertHasOfficialCodeStyle()
            project.checkKotlinSettings()
            assertCorrectContent()
            assertModules(project, "project", "module")
            assertTrue("src folder should exist", resolvePathInProject("project/module/src").exists())
        }
    }

    private fun createJavaProject(): Project {
        return createProjectFromTemplate(JAVA) { step ->
            val languageData = step.languageData!!
            languageData.language = "Java"
            languageData.name = "project"
        }
    }

    fun testNewModuleNoExistingRuntime() {
        // Simple Java project without runtime
        createJavaProject().useProject { project ->
            project.assertDoesNotHaveKotlinJavaRuntime()
            project.checkKotlinSettings(expectedJvmTarget = null, expectedApiVersion = null, expectedLanguageVersion = null)
            project.createModuleWithData("module", "project")
            project.assertHasKotlinJavaRuntime()
            project.assertHasOfficialCodeStyle()
            project.checkKotlinSettings()
            assertModules(project, "project", "module")
            assertCorrectContent()
        }
    }

    private fun Library.ModifiableModel.addKotlinLibraryRoots() {
        addRoot(VfsUtil.getUrlForLibraryRoot(TestKotlinArtifacts.kotlinStdlib), OrderRootType.CLASSES)
        addRoot(VfsUtil.getUrlForLibraryRoot(TestKotlinArtifacts.kotlinStdlib), OrderRootType.SOURCES)
    }

    private fun Project.createKotlinLibrary(libraryName: String = "SomeOtherRuntimeName", addToModule: String?) {
        runWriteAction {
            val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(this).modifiableModel
            val newLibrary = libraryTable.createLibrary(libraryName)
            libraryTable.commit()

            newLibrary.modifiableModel.apply { addKotlinLibraryRoots() }.commit()
            addToModule?.let {
                modules.first { it.name == addToModule }.addDependency(newLibrary)
            }
        }
    }

    fun testAddKotlinRuntimeWithoutDependencyOnProjectLibrary() {
        createJavaProject().useProject { project ->
            project.createKotlinLibrary(addToModule = null)
            project.assertDoesNotHaveKotlinJavaRuntime()
            project.createModuleWithData("module", "project")
            project.assertHasOfficialCodeStyle()
            project.assertHasKotlinJavaRuntime()
            assertModules(project, "project", "module")
            assertCorrectContent()
        }
    }

    fun testNewModuleSingleOtherRuntime() {
        createJavaProject().useProject { project ->
            project.createKotlinLibrary(addToModule = "project")
            project.assertDoesNotHaveKotlinJavaRuntime()
            project.createModuleWithData("module", "project")
            project.assertHasOfficialCodeStyle()
            project.assertDoesNotHaveKotlinJavaRuntime()
            assertModules(project, "project", "module")
            assertCorrectContent()
        }
    }

    fun testNewModuleMultipleOtherRuntimes() {
        createJavaProject().useProject { project ->
            project.createKotlinLibrary(libraryName = "SomeOtherRuntimeName", addToModule = "project")
            project.createKotlinLibrary(libraryName = "SecondLibrary", addToModule = "project")
            project.assertDoesNotHaveKotlinJavaRuntime()
            UiInterceptors.register(object : UiInterceptor<StdlibVersionChooserDialog>(StdlibVersionChooserDialog::class.java) {
                override fun doIntercept(dialog: StdlibVersionChooserDialog) {
                    Disposer.register(testRootDisposable, dialog.disposable)
                    val libraries = dialog.availableLibraries
                    assertEquals(2, libraries.size)
                    assertEquals(setOf("SecondLibrary", "SomeOtherRuntimeName"), libraries.map { it.value.libraryName }.toSet())
                }
            })
            try {
                project.createModuleWithData("module", "project")
                project.assertHasOfficialCodeStyle()
            } finally {
                UiInterceptors.clear()
            }
        }
    }

    fun testNewModuleExistingModuleLevelKotlinLibrary() {
        createJavaProject().useProject { project ->
            runWriteAction {
                val modifiableModel = ModuleRootManager.getInstance(project.modules.first()).modifiableModel
                val moduleLibraryTable = modifiableModel.moduleLibraryTable
                val library = moduleLibraryTable.createLibrary("ModuleLevelLib")
                library.modifiableModel.apply { addKotlinLibraryRoots() }.commit()
                modifiableModel.commit()
            }
            project.assertDoesNotHaveKotlinJavaRuntime()
            project.createModuleWithData("module", "project")
            project.assertHasKotlinJavaRuntime()
            project.assertHasOfficialCodeStyle()
            assertModules(project, "project", "module")
            assertCorrectContent()
        }
    }

    fun testKeepExistingKotlinSettings() {
        createProjectWithData().useProject { project ->
            project.updateKotlinSettings("11", "1.8", "1.7")
            project.createModuleWithData("module", "project")
            project.checkKotlinSettings("11", "1.8", "1.7")
            assertModules(project, "project", "module")
            assertCorrectContent()
        }
    }

    fun testInheritProjectSdk() {
        createProjectWithData().useProject { project ->
            val createdModule = project.createModuleWithData("module", "project")
            assertModules(project, "project", "module")
            assertCorrectContent()
            val mainModule = project.modules.first { it.name == "project" }
            assertEquals(mySdk, ProjectRootManager.getInstance(project).projectSdk)
            assertEquals(mySdk, ModuleRootManager.getInstance(mainModule).sdk)
            assertEquals(mySdk, ModuleRootManager.getInstance(createdModule).sdk)
        }
    }

    fun testCustomModuleSdk() {
        createProjectWithData().useProject { project ->
            val createdModule = project.createModuleWithData("module", "project", sdk = otherSdk)
            assertModules(project, "project", "module")
            assertCorrectContent()
            val mainModule = project.modules.first { it.name == "project" }
            assertEquals(mySdk, ProjectRootManager.getInstance(project).projectSdk)
            assertEquals(mySdk, ModuleRootManager.getInstance(mainModule).sdk)
            assertEquals(otherSdk, ModuleRootManager.getInstance(createdModule).sdk)
        }
    }

    fun testKeepObsoleteCodeStyle() {
        createProjectWithData().useProject { project ->
            ProjectCodeStyleImporter.apply(project, KotlinObsoleteCodeStyle.INSTANCE)
            project.createModuleWithData("module", "project", sdk = otherSdk)
            assertModules(project, "project", "module")
            assertCorrectContent()
            project.assertHasOfficialCodeStyle(false)
            assertEquals(KotlinObsoleteStyleGuide.CODE_STYLE_ID, CodeStyle.getSettings(project).kotlinCodeStyleDefaults())
        }
    }
}