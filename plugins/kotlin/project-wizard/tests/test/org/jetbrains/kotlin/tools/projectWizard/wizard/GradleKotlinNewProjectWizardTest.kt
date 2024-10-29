// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.GRADLE
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.KOTLIN
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.io.getResolvedPath
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.withValue
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.utils.module.assertModules
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.kotlinBuildSystemData
import org.jetbrains.kotlin.tools.projectWizard.gradle.GradleKotlinNewProjectWizardData.Companion.kotlinGradleData
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardStep.GradleDsl
import org.jetbrains.plugins.gradle.setup.GradleCreateProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.util.ProjectInfo
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.junit.jupiter.api.*
import java.io.File

/**
 * A test case for the Kotlin Gradle new project/module wizard.
 * Test cases can either generate a new project, or a new module in an existing projects.
 * Existing projects can be defined using the [projectInfo] DSL.
 * The tests work by asserting that certain generated files are created with the correct content,
 * the files are located in the testData/newProjectWizard/<testName> folder.
 * Only files that are mentioned in these folders are asserted to be generated correctly.
 */
@TestRoot("project-wizard/tests")
class GradleKotlinNewProjectWizardTest : GradleCreateProjectTestCase(), NewKotlinProjectTestUtils {

    override var testDirectory = "testData/gradleNewProjectWizard"

    private lateinit var testInfo: TestInfo

    @BeforeEach
    fun init(testInfo: TestInfo) {
        this.testInfo = testInfo
    }

    @AfterEach
    fun tearDown() {
        runAll({ KotlinSdkType.removeKotlinSdkInTests() })
    }

    override fun getTestFolderName(): String {
        return testInfo.displayName.takeWhile { it != '(' }.removePrefix("test").decapitalizeAsciiOnly()
    }

    private fun Project.findKotlinVersion(useKotlinDsl: Boolean, modulePath: String? = null): String? {
        val path = StringBuilder().apply {
            if (modulePath != null) {
                append(modulePath)
                append("/")
            }
            append("build.gradle")
            if (useKotlinDsl) {
                append(".kts")
            }
        }.toString()
        val buildFile = findRelativeFile(path)
        Assertions.assertNotNull(buildFile, "Could not find build file at $path")
        return kotlinVersionRegex.find(buildFile!!.readText())?.groupValues?.get(1)
    }

    private fun Project.assertKotlinVersion(expectedVersion: String, useKotlinDsl: Boolean, modulePath: String? = null) {
        val kotlinVersion = findKotlinVersion(useKotlinDsl, modulePath)
        Assertions.assertNotNull(kotlinVersion, "Could not find Kotlin version in build file")
        Assertions.assertEquals(expectedVersion, kotlinVersion)
    }

    private val kotlinVersionRegex = Regex("""kotlin.*version.*["']([\w-.]*)["']""")
    override fun substituteArtifactsVersions(str: String): String {
        return str.replaceFirstGroup(kotlinVersionRegex, "KOTLIN_VERSION")
    }

    private val jvmToolchainVersionRegex = Regex("""jvmToolchain\((\d+)\)""")
    private fun substituteJvmToolchainVersion(str: String): String {
        return str.replaceFirstGroup(jvmToolchainVersionRegex, "JDK_VERSION")
    }

    private val foojayRegex = Regex("""foojay-resolver-convention.*version.*["']([\w-.]*)["']""")
    private fun substituteFoojayVersion(str: String): String {
        return str.replaceFirstGroup(foojayRegex, "FOOJAY_VERSION")
    }

    private fun tomlVersionRegex(libraryName: String) = Regex("""$libraryName = "(\d+\.\d+\.\d+)"""")
    private fun substituteTomlLibraryVersion(str: String, libraryName: String, libraryReplacementName: String): String {
        return str.replaceFirstGroup(tomlVersionRegex(libraryName), libraryReplacementName)
    }

    // We replace dynamic values like the Kotlin version that is used with placeholders.
    // That way we do not have to update tests every time a new Kotlin version releases.
    override fun postprocessOutputFile(relativePath: String, fileContents: String): String {
        return if (relativePath.contains("build.gradle")) {
            substituteArtifactsVersions(substituteJvmToolchainVersion(fileContents))
        } else if (relativePath.contains("settings.gradle")) {
            substituteFoojayVersion(fileContents)
        } else if (relativePath.contains("gradle.kts")) { // convention plugin
            substituteArtifactsVersions(substituteJvmToolchainVersion(fileContents))
        } else if (relativePath.contains("libs.versions.toml")) {
            var newContents = fileContents
            newContents = substituteTomlLibraryVersion(newContents, "kotlin", "KOTLIN_VERSION")
            newContents = substituteTomlLibraryVersion(newContents, "kotlinxDatetime", "KOTLINX_DATETIME_VERSION")
            newContents = substituteTomlLibraryVersion(newContents, "kotlinxSerializationJSON", "KOTLINX_SERIALIZATION_JSON_VERSION")
            newContents = substituteTomlLibraryVersion(newContents, "kotlinxCoroutines", "KOTLINX_COROUTINES_VERSION")
            newContents
        } else fileContents
    }

    private fun NewProjectWizardStep.setGradleWizardData(
        useKotlinDsl: Boolean = false,
        name: String = "project",
        path: String = name,
        groupId: String = "org.testcase",
        version: String = "1.0.0",
        addSampleCode: Boolean = false,
        generateOnboardingTips: Boolean = false,
        parentData: ProjectData? = null,
        generateMultipleModules: Boolean = false,
    ) {
        baseData!!.name = name
        baseData!!.path = testRoot.toNioPath().getResolvedPath(path).parent.toCanonicalPath()
        kotlinBuildSystemData!!.buildSystem = GRADLE
        kotlinGradleData!!.parentData = parentData
        kotlinGradleData!!.generateMultipleModules = generateMultipleModules
        kotlinGradleData!!.gradleDsl = if (useKotlinDsl) GradleDsl.KOTLIN else GradleDsl.GROOVY
        kotlinGradleData!!.groupId = groupId
        kotlinGradleData!!.artifactId = name
        kotlinGradleData!!.version = version
        kotlinGradleData!!.addSampleCode = addSampleCode
        kotlinGradleData!!.generateOnboardingTips = generateOnboardingTips
    }

    private fun runNewProjectTestCase(
        expectedModules: List<String> = listOf("project", "project.main", "project.test"),
        useKotlinDsl: Boolean = false,
        name: String = "project",
        groupId: String = "org.testcase",
        version: String = "1.0.0",
        addSampleCode: Boolean = false,
        generateOnboardingTips: Boolean = false,
        generateMultipleModules: Boolean = false,
        additionalAssertions: (Project) -> Unit = {}
    ) {

        runBlocking {
            createProjectByWizard(KOTLIN) {
                setGradleWizardData(
                    useKotlinDsl = useKotlinDsl,
                    name = name,
                    groupId = groupId,
                    version = version,
                    parentData = null,
                    path = name,
                    addSampleCode = addSampleCode,
                    generateOnboardingTips = generateOnboardingTips,
                    generateMultipleModules = generateMultipleModules,
                )
            }.useProjectAsync { project ->
                assertModules(project, expectedModules)
                project.assertCorrectProjectFiles()
                additionalAssertions(project)
            }
        }
    }

    @Test
    fun testSimpleProject() {
        runNewProjectTestCase()
    }

    @Test
    fun testSimpleProjectKts() {
        runNewProjectTestCase(useKotlinDsl = true)
    }

    @Test
    fun testSampleCode() {
        runNewProjectTestCase(addSampleCode = true)
    }

    @Test
    fun testSampleCodeKts() {
        runNewProjectTestCase(useKotlinDsl = true, addSampleCode = true)
    }

    private val expectedMultiModuleProjectModules = listOf(
        "project",
        "project.app",
        "project.utils",
        "project.buildSrc",
        "project.app.main",
        "project.app.test",
        "project.utils.main",
        "project.utils.test",
        "project.buildSrc.main",
        "project.buildSrc.test"
    )

    @Test
    fun testNoMultiModuleProjectForGroovy() {
        runNewProjectTestCase(
            generateMultipleModules = true,
        )
    }

    @Test
    fun testMultiModuleProjectKts() {
        runNewProjectTestCase(
            useKotlinDsl = true,
            addSampleCode = true,
            generateOnboardingTips = false,
            expectedModules = expectedMultiModuleProjectModules,
            generateMultipleModules = true
        )
    }

    @Test
    fun testMultiModuleProjectOnboardingTipsKts() {
        runNewProjectTestCase(
            useKotlinDsl = true,
            addSampleCode = true,
            generateOnboardingTips = true,
            expectedModules = expectedMultiModuleProjectModules,
            generateMultipleModules = true
        )
    }

    @Test
    fun testMultiModuleProjectEmptyKts() {
        runNewProjectTestCase(
            useKotlinDsl = true,
            addSampleCode = false,
            generateOnboardingTips = false,
            expectedModules = expectedMultiModuleProjectModules,
            generateMultipleModules = true
        ) { project ->
            val basePath = File(project.basePath!!)
            val hasKotlinFile = basePath.walkTopDown().none { it.extension == "kt" }
            Assertions.assertFalse(hasKotlinFile, "empty project should not contain Kotlin files")
        }
    }

    @Test
    fun testOnboardingTips() {
        Registry.get("doc.onboarding.tips.render").withValue(false) {
            runNewProjectTestCase(addSampleCode = true, generateOnboardingTips = true) { project ->
                val mainFileContent = project.findMainFileContent()
                Assertions.assertNotNull(mainFileContent, "Could not find Main.kt file")
                Assertions.assertTrue(
                    mainFileContent!!.contains(ONBOARDING_TIPS_SEARCH_STR),
                    "Main file did not contain onboarding tips"
                )
                Assertions.assertFalse(
                    mainFileContent.contains("//TIP"),
                    "Main file contained rendered onboarding tips"
                )
            }
        }
    }

    // The onboarding tips have to be handled a bit more manually because the rendered text depends on the OS of the system
    // because shortcuts are OS specific
    @Test
    fun testOnboardingTipsKts() {
        Registry.get("doc.onboarding.tips.render").withValue(false) {
            runNewProjectTestCase(useKotlinDsl = true, addSampleCode = true, generateOnboardingTips = true) { project ->
                val mainFileContent = project.findMainFileContent()
                Assertions.assertNotNull(mainFileContent, "Could not find Main.kt file")
                Assertions.assertTrue(
                    mainFileContent!!.contains(ONBOARDING_TIPS_SEARCH_STR),
                    "Main file did not contain onboarding tips"
                )
                Assertions.assertFalse(
                    mainFileContent.contains("//TIP"),
                    "Main file contained rendered onboarding tips"
                )
            }
        }
    }

    @Test
    fun testRenderedOnboardingTips() {
        Registry.get("doc.onboarding.tips.render").withValue(true) {
            runNewProjectTestCase(addSampleCode = true, generateOnboardingTips = true) { project ->
                val mainFileContent = project.findMainFileContent()
                Assertions.assertNotNull(mainFileContent, "Could not find Main.kt file")
                Assertions.assertTrue(
                    mainFileContent!!.contains(ONBOARDING_TIPS_SEARCH_STR),
                    "Main file did not contain onboarding tips"
                )
                Assertions.assertTrue(
                    mainFileContent.contains("//TIP"),
                    "Main file contained rendered onboarding tips"
                )
            }
        }
    }

    @Test
    fun testRenderedOnboardingTipsKts() {
        Registry.get("doc.onboarding.tips.render").withValue(true) {
            runNewProjectTestCase(useKotlinDsl = true, addSampleCode = true, generateOnboardingTips = true) { project ->
                val mainFileContent = project.findMainFileContent()
                Assertions.assertNotNull(mainFileContent, "Could not find Main.kt file")
                Assertions.assertTrue(
                    mainFileContent!!.contains(ONBOARDING_TIPS_SEARCH_STR),
                    "Main file did not contain onboarding tips"
                )
                Assertions.assertTrue(
                    mainFileContent.contains("//TIP"),
                    "Main file contained rendered onboarding tips"
                )
            }
        }
    }

    private fun runNewModuleTestCase(
        expectedNewModules: List<String>,
        projectInfo: ProjectInfo,
        parentPath: String = "project",
        useKotlinDsl: Boolean = false,
        name: String = "module",
        groupId: String = "org.testcase",
        version: String = "1.0.0",
        addSampleCode: Boolean = false,
        generateOnboardingTips: Boolean = false,
        independentHierarchy: Boolean = false,
        generateMultipleModules: Boolean = false,
        additionalAssertions: (Project) -> Unit = {}
    ) {
        runBlocking {
            initProject(projectInfo)
            openProject("project").useProjectAsync { project ->
                val parentData = ExternalSystemApiUtil.findProjectNode(project, SYSTEM_ID, testRoot.path + "/$parentPath")!!
                val existingModules = project.modules.map { it.name }
                createModuleByWizard(project, KOTLIN) {
                    setGradleWizardData(
                        useKotlinDsl = useKotlinDsl,
                        name = name,
                        groupId = groupId,
                        version = version,
                        parentData = parentData.data.takeUnless { independentHierarchy },
                        path = "$parentPath/$name",
                        addSampleCode = addSampleCode,
                        generateOnboardingTips = generateOnboardingTips,
                        generateMultipleModules = generateMultipleModules,
                    )
                }
                assertModules(project, existingModules + expectedNewModules)
                project.assertCorrectProjectFiles()
                additionalAssertions(project)
            }
        }
    }

    private fun simpleJavaProject(useKotlinDsl: Boolean) = projectInfo("project", useKotlinDsl) {
        withJavaBuildFile()
        withSettingsFile {
            setProjectName("project")
        }
    }

    @Test
    fun testNewModuleInJavaProject() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = simpleJavaProject(false),
            addSampleCode = true
        ) { project ->
            val propertiesExist = project.findRelativeFile("module/gradle.properties")?.exists() == true
            Assertions.assertFalse(propertiesExist, "Gradle properties file should not exist in modules")
        }
    }

    @Test
    fun testNewModuleInJavaProjectKts() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = simpleJavaProject(true),
            addSampleCode = true,
            useKotlinDsl = true
        ) { project ->
            val propertiesExist = project.findRelativeFile("module/gradle.properties")?.exists() == true
            Assertions.assertFalse(propertiesExist, "Gradle properties file should not exist in modules")
        }
    }

    @Test
    fun testNewModuleInJavaProjectMixed() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = simpleJavaProject(true)
        )
    }

    private fun simpleKotlinProject(useKotlinDsl: Boolean) = projectInfo("project", useKotlinDsl) {
        withBuildFile {
            addGroup(groupId)
            addVersion(version)
            withKotlinJvmPlugin("1.9.0")
        }
        withSettingsFile {
            setProjectName("project")
        }
    }

    @Test
    fun testNewModuleInKotlinProject() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = simpleKotlinProject(false)
        )
    }

    @Test
    fun testNewModuleInKotlinProjectKts() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = simpleKotlinProject(true),
            useKotlinDsl = true
        )
    }

    @Test
    fun testNewModuleInKotlinProjectIndependentHierarchy() {
        runNewModuleTestCase(
            expectedNewModules = listOf("module", "module.main", "module.test"),
            projectInfo = simpleKotlinProject(false),
            independentHierarchy = true
        )
    }

    @Test
    fun testNewModuleInKotlinProjectIndependentHierarchyKts() {
        runNewModuleTestCase(
            expectedNewModules = listOf("module", "module.main", "module.test"),
            projectInfo = simpleKotlinProject(true),
            useKotlinDsl = true,
            independentHierarchy = true
        )
    }

    @Test
    fun testNoMultiModuleProjectForNewModulesKts() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = simpleKotlinProject(true),
            useKotlinDsl = true,
        )
    }

    private fun javaProjectWithKotlinSubmodule(useKotlinDsl: Boolean) = projectInfo("project", useKotlinDsl) {
        withJavaBuildFile()
        moduleInfo("othermodule", "othermodule", useKotlinDsl) {
            withBuildFile {
                addGroup(groupId)
                addVersion(version)
                withKotlinJvmPlugin("1.8.0")
            }
        }
        withSettingsFile {
            setProjectName("project")
            include("othermodule")
        }
    }

    @Test
    fun testOtherKotlinModule() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = javaProjectWithKotlinSubmodule(false)
        ) { project ->
            project.assertKotlinVersion("1.8.0", false, "module")
        }
    }

    @Test
    fun testOtherKotlinModuleKts() {
        runNewModuleTestCase(
            expectedNewModules = listOf("project.module", "project.module.main", "project.module.test"),
            projectInfo = javaProjectWithKotlinSubmodule(true),
            useKotlinDsl = true
        ) { project ->
            project.assertKotlinVersion("1.8.0", true, "module")
        }
    }

    private fun simpleBuildSrcProjectWithVersionCatalog(useDependency: Boolean) = projectInfo("project") {
        moduleInfo("project.buildSrc", "buildSrc") {
            withBuildFile {
                if (useDependency) {
                    withDependency {
                        addElement(code("libs.kotlinGradlePlugin"))
                    }
                }
            }
            withSettingsFile {
                addCode(
                    """
                        dependencyResolutionManagement {
        
                            // Use Maven Central and Gradle Plugin Portal for resolving dependencies in the shared build logic ("buildSrc") project
                            @Suppress("UnstableApiUsage")
                            repositories {
                                mavenCentral()
                            }
        
                            // Re-use the version catalog from the main build
                            versionCatalogs {
                                create("libs") {
                                    from(files("../gradle/libs.versions.toml"))
                                }
                            }
                        }
                    """.trimIndent()
                )
            }
        }
        withSettingsFile {
            setProjectName("project")
        }
        withFile(
            "gradle/libs.versions.toml",
            """
                 [versions]
                 kotlin = "2.0.21"

                 [libraries]
                 kotlinGradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
             """.trimIndent()
        )
    }

    @Test
    fun testNewModuleWithVersionCatalog() {
        runNewModuleTestCase(
            useKotlinDsl = false,
            expectedNewModules = listOf("module"),
            projectInfo = simpleBuildSrcProjectWithVersionCatalog(true)
        ) { project ->
            // It should not specify an explicit version because it is defined in the version catalog
            Assertions.assertNull(project.findKotlinVersion(false, "module"))
        }
    }

    @Test
    fun testNewModuleWithVersionCatalogKts() {
        runNewModuleTestCase(
            useKotlinDsl = true,
            expectedNewModules = listOf("module"),
            projectInfo = simpleBuildSrcProjectWithVersionCatalog(true)
        ) { project ->
            // It should not specify an explicit version because it is defined in the version catalog
            Assertions.assertNull(project.findKotlinVersion(true, "module"))
        }
    }

    @Test
    fun testNewModuleWithUnusedVersionCatalog() {
        runNewModuleTestCase(
            useKotlinDsl = false,
            expectedNewModules = listOf("project.module.main", "project.module.test", "project.module"),
            projectInfo = simpleBuildSrcProjectWithVersionCatalog(false)
        ) { project ->
            Assertions.assertNotNull(project.findKotlinVersion(false, "module"))
        }
    }

    @Test
    fun testNewModuleWithUnusedVersionCatalogKts() {
        runNewModuleTestCase(
            useKotlinDsl = true,
            expectedNewModules = listOf("project.module.main", "project.module.test", "project.module"),
            projectInfo = simpleBuildSrcProjectWithVersionCatalog(false)
        ) { project ->
            Assertions.assertNotNull(project.findKotlinVersion(true, "module"))
        }
    }
}