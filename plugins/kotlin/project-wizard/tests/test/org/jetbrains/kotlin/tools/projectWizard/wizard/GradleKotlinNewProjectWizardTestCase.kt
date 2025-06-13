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
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.kotlinBuildSystemData
import org.jetbrains.kotlin.tools.projectWizard.gradle.GradleKotlinNewProjectWizardData.Companion.kotlinGradleData
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.setup.GradleNewProjectWizardTestCase
import org.jetbrains.plugins.gradle.testFramework.util.ModuleInfo
import org.jetbrains.plugins.gradle.testFramework.util.ProjectInfo
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.junit.jupiter.api.*

@TestRoot("project-wizard/tests")
abstract class GradleKotlinNewProjectWizardTestCase : GradleNewProjectWizardTestCase(), NewKotlinProjectTestUtils {

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

    fun Project.findKotlinVersion(useKotlinDsl: Boolean, modulePath: String? = null): String? {
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

    fun Project.assertKotlinVersion(expectedVersion: String, useKotlinDsl: Boolean, modulePath: String? = null) {
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

    private val daemonJvmCriteriaRegex = Regex("""toolchainVersion=(\d+)""")
    private fun substituteDaemonJvmCriteriaVersions(str: String): String {
        return str.replaceFirstGroup(daemonJvmCriteriaRegex, "TOOLCHAIN_VERSION")
    }

    // We replace dynamic values like the Kotlin version that is used with placeholders.
    // That way we do not have to update tests every time a new Kotlin version releases.
    override fun postprocessOutputFile(relativePath: String, fileContents: String): String {
        return if (relativePath.contains("build.gradle")) { // covers build.gradle and build.gradle.kts
            var newContents = fileContents
            newContents = substituteArtifactsVersions(newContents)
            newContents = substituteJvmToolchainVersion(newContents)
            newContents
        } else if (relativePath.contains("settings.gradle")) { // covers settings.gradle and settings.gradle.kts
            var newContents = fileContents
            newContents = substituteFoojayVersion(newContents)
            newContents = substituteDaemonJvmCriteriaVersions(newContents)
            newContents
        } else if (relativePath.contains("gradle.kts")) { // convention plugin
            var newContents = fileContents
            newContents = substituteArtifactsVersions(newContents)
            newContents = substituteJvmToolchainVersion(newContents)
            newContents
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
        parentData: ProjectData? = null,
        generateMultipleModules: Boolean = false,
    ) {
        baseData!!.name = name
        baseData!!.path = testRoot.toNioPath().getResolvedPath(path).parent.toCanonicalPath()
        kotlinBuildSystemData!!.buildSystem = GRADLE
        kotlinGradleData!!.parentData = parentData
        kotlinGradleData!!.generateMultipleModules = generateMultipleModules
        kotlinGradleData!!.gradleDsl = GradleDsl.valueOf(useKotlinDsl)
        kotlinGradleData!!.groupId = groupId
        kotlinGradleData!!.artifactId = name
        kotlinGradleData!!.version = version
        kotlinGradleData!!.addSampleCode = addSampleCode
    }

    fun ModuleInfo.Builder.withJavaBuildFile() {
        withBuildFile {
            addGroup(groupId)
            addVersion(version)
            withJavaPlugin()
            withJUnit()
        }
    }

    fun runNewProjectTestCase(
        expectedModules: List<String> = listOf("project", "project.main", "project.test"),
        useKotlinDsl: Boolean = false,
        name: String = "project",
        groupId: String = "org.testcase",
        version: String = "1.0.0",
        addSampleCode: Boolean = false,
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
                    generateMultipleModules = generateMultipleModules,
                )
            }.useProjectAsync { project ->
                assertModules(project, expectedModules)
                project.assertCorrectProjectFiles()
                additionalAssertions(project)
            }
        }
    }

    fun runNewModuleTestCase(
        expectedNewModules: List<String>,
        projectInfo: ProjectInfo,
        parentPath: String = "project",
        useKotlinDsl: Boolean = false,
        name: String = "module",
        groupId: String = "org.testcase",
        version: String = "1.0.0",
        addSampleCode: Boolean = false,
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
                        generateMultipleModules = generateMultipleModules,
                    )
                }
                assertModules(project, existingModules + expectedNewModules)
                project.assertCorrectProjectFiles()
                additionalAssertions(project)
            }
        }
    }
}